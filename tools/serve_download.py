"""Serve only the published APK and an install page on the selected LAN address.

No directory listing, uploads, tunneling or access to application data/signing files.
Run with Start-Download.ps1 to discover the Wi-Fi IP and generate a scannable QR.
"""
import argparse
import hashlib
import html
import ipaddress
import json
import re
import socket
import struct
import sys
import threading
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from string import Template
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
DEPS = ROOT / ".local-download-deps"
if DEPS.is_dir():
    sys.path.insert(0, str(DEPS))


def release_info(root=ROOT):
    """Only a published root APK matching the app's current version can be downloaded."""
    build = (root / "android/app/build.gradle.kts").read_text(encoding="utf-8")
    version_match = re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+(?:\.[0-9]+)?)"', build)
    if version_match is None:
        raise ValueError("Expected a major.minor or legacy major.minor.patch version")
    version = version_match.group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', build).group(1))
    short_version = version.count(".") == 1
    filename = f"Duolian-V{version}.apk" if short_version else f"Duolian-{version}-debug.apk"
    apk = root / filename
    if not apk.is_file() or apk.is_symlink():
        raise FileNotFoundError(f"Published APK not found: {apk.name}")
    stat = apk.stat()
    if not 0 < stat.st_size < 200 * 1024 * 1024:
        raise ValueError("Unexpected APK size")
    return dict(version=version, displayVersion=f"V{version}", versionCode=code, filename=apk.name, size=stat.st_size,
                sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), apk=apk)


def qr_png(url):
    """Create a standard monochrome QR with quiet zone; PNG uses only stdlib."""
    import qrcode
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=10, border=4)
    qr.add_data(url)
    qr.make(fit=True)
    matrix = qr.get_matrix()
    scale = 10
    pixels = bytearray()
    for row in matrix:
        scan = b"\x00" + b"".join((b"\x00" if cell else b"\xff") * scale for cell in row)
        pixels.extend(scan * scale)
    side = len(matrix) * scale
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", side, side, 8, 0, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(pixels, 9)) + chunk(b"IEND", b""))


def valid_bind(value):
    address = ipaddress.ip_address(value)
    if address.version != 4 or not (address.is_private or address.is_loopback) or value == "0.0.0.0":
        raise ValueError("Bind to a specific private LAN IPv4 address or 127.0.0.1")
    return value


def make_server(bind, port, output, root=ROOT):
    valid_bind(bind)
    base = f"http://{bind}:{port}"
    qr = qr_png(base + "/")
    page = Template((root / "tools/download_page.html").read_text(encoding="utf-8"))
    lock = threading.Lock()
    cached = None
    signature = None
    def current():
        nonlocal cached, signature
        # A running server follows a newly published version, without changing its URL.
        build_stat = (root / "android/app/build.gradle.kts").stat()
        stamp = (build_stat.st_mtime_ns, build_stat.st_size)
        with lock:
            if cached is None or stamp != signature or cached["apk"].stat().st_mtime_ns != cached["mtime"]:
                updated = release_info(root)
                updated["mtime"] = updated["apk"].stat().st_mtime_ns
                cached, signature = updated, stamp
            return cached.copy()
    current()
    output.mkdir(parents=True, exist_ok=True)
    (output / "download-qr.png").write_bytes(qr)
    (output / "download-url.txt").write_text(base + "/\n", encoding="utf-8")

    class Handler(BaseHTTPRequestHandler):
        server_version = "DuolianDownload/1"
        def do_HEAD(self):
            self.respond(head=True)
        def do_GET(self):
            self.respond(head=False)
        def respond(self, head):
            # Host does not affect QR/links or file resolution; only an exact allowlist is served.
            path = urlsplit(self.path).path
            if path not in {"/", "/index.html", "/latest.apk", "/latest.json", "/qr.png", "/health"}:
                self.send_error(404)
                return
            try:
                info = current()
            except (OSError, ValueError):
                self.send_error(503, "The next release is being prepared. Try again shortly.")
                return
            if path == "/latest.apk":
                size = info["size"]
                start, end = 0, size - 1
                partial = self.headers.get("Range")
                if self.headers.get("If-Range") and self.headers["If-Range"] != f'"{info["sha256"]}"':
                    partial = None
                if partial:
                    match = re.fullmatch(r"bytes=(\d*)-(\d*)", partial.strip())
                    if not match or not any(match.groups()):
                        self.send_error(416)
                        return
                    first, last = match.groups()
                    if first:
                        start = int(first); end = min(int(last), end) if last else end
                    else:
                        start = max(size - int(last), 0)
                    if start > end or start >= size:
                        self.send_response(416)
                        self.send_header("Content-Range", f"bytes */{size}")
                        self.send_header("Content-Length", "0")
                        self.end_headers()
                        return
                self.send_response(206 if partial else 200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                download_name = info["filename"] if info["version"].count(".") == 1 else f'Duolian-{info["version"]}.apk'
                self.send_header("Content-Disposition", f'attachment; filename="{download_name}"')
                self.send_header("Content-Length", str(end - start + 1))
                self.send_header("Accept-Ranges", "bytes")
                self.send_header("ETag", f'"{info["sha256"]}"')
                if partial:
                    self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
                self.finish_headers()
                if not head:
                    try:
                        with info["apk"].open("rb") as apk:
                            apk.seek(start)
                            remaining = end - start + 1
                            while remaining:
                                block = apk.read(min(65536, remaining))
                                if not block:
                                    break
                                self.wfile.write(block)
                                remaining -= len(block)
                    except (BrokenPipeError, ConnectionResetError):
                        pass
                return
            if path in {"/latest.json", "/health"}:
                data = {key: value for key, value in info.items() if key not in {"apk", "mtime"}}
                data.update(downloadUrl=base + "/latest.apk", pageUrl=base + "/")
                body = json.dumps(data, ensure_ascii=False).encode("utf-8")
                content_type = "application/json; charset=utf-8"
            elif path == "/qr.png":
                body, content_type = qr, "image/png"
            else:
                body = page.substitute(version=html.escape(info["displayVersion"]), size=f'{info["size"] / 1024 / 1024:.1f}',
                    sha=info["sha256"], url=html.escape(base + "/")).encode("utf-8")
                content_type = "text/html; charset=utf-8"
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.finish_headers()
            if not head:
                self.wfile.write(body)
        def finish_headers(self):
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Referrer-Policy", "no-referrer")
            self.end_headers()
        def log_message(self, fmt, *args):
            print(self.log_date_time_string(), fmt % args, flush=True)

    server = ThreadingHTTPServer((bind, port), Handler)
    server.daemon_threads = True
    return server, base


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bind", required=True)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--output", type=Path, default=ROOT / ".local-download")
    args = parser.parse_args()
    server, base = make_server(args.bind, args.port, args.output)
    print(f"Duolian download ready: {base}/", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
