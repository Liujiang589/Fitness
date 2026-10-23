"""HTTP behavior and exposure boundaries for the small LAN release server."""
import hashlib
import io
import json
import socket
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from urllib.parse import quote
from pathlib import Path
from serve_download import ROOT, make_server, qr_png, valid_bind


class DownloadTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        (cls.root / "android/app").mkdir(parents=True)
        (cls.root / "tools").mkdir()
        (cls.root / "tools/download_page.html").write_bytes((ROOT / "tools/download_page.html").read_bytes())
        (cls.root / "tools/update-notes.json").write_text(json.dumps({"0.11.1": ["改进训练体验"]}), encoding="utf-8")
        (cls.root / "android/app/build.gradle.kts").write_text('versionName = "0.11.1"\nversionCode = 14\n')
        cls.apk = b"PK\x03\x04" + bytes(range(256)) * 50
        (cls.root / "Duolian-0.11.1-debug.apk").write_bytes(cls.apk)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        cls.server, cls.base = make_server("127.0.0.1", port, cls.root / "out", root=cls.root)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()
        cls.temp.cleanup()

    def request(self, path, **kwargs):
        return urllib.request.urlopen(urllib.request.Request(self.base + path, **kwargs), timeout=5)

    def test_full_apk_is_exact_and_has_installable_name(self):
        with self.request("/latest.apk") as response:
            self.assertEqual(self.apk, response.read())
            self.assertEqual("application/vnd.android.package-archive", response.headers["Content-Type"])
            self.assertIn('filename="Duolian-0.11.1.apk"', response.headers["Content-Disposition"])

    def test_resume_download_returns_correct_slice(self):
        with self.request("/latest.apk", headers={"Range": "bytes=10-99"}) as response:
            self.assertEqual(206, response.status)
            self.assertEqual(self.apk[10:100], response.read())

    def test_changed_etag_forces_full_download_instead_of_mixing_versions(self):
        with self.request("/latest.apk", headers={"Range": "bytes=10-99", "If-Range": '"old-version"'}) as response:
            self.assertEqual(200, response.status)
            self.assertEqual(self.apk, response.read())

    def test_invalid_ranges_are_rejected(self):
        for value in ["bytes=999999-", "bytes=20-10", "bytes=0-2,4-6", "bytes=-0"]:
            with self.assertRaises(urllib.error.HTTPError) as context:
                self.request("/latest.apk", headers={"Range": value})
            self.assertEqual(416, context.exception.code)

    def test_only_release_routes_are_exposed(self):
        for path in ["/README.md", "/android/.local-signing/duolian-debug.keystore", "/../README.md", "/%2e%2e/README.md", "/tools/", "/tools/update-notes.json"]:
            with self.assertRaises(urllib.error.HTTPError) as context:
                self.request(path)
            self.assertEqual(404, context.exception.code)

    def test_manifest_and_head_describe_the_same_release(self):
        with self.request("/latest.json") as response:
            manifest = json.load(response)
        self.assertEqual("0.11.1", manifest["version"])
        self.assertEqual("app.duolian.local", manifest["packageName"])
        self.assertEqual(26, manifest["minSdk"])
        self.assertEqual(["改进训练体验"], manifest["releaseNotes"])
        self.assertEqual(hashlib.sha256(self.apk).hexdigest(), manifest["sha256"])
        with self.request("/latest.apk", method="HEAD") as response:
            self.assertEqual(str(len(self.apk)), response.headers["Content-Length"])
            self.assertEqual(b"", response.read())

    def test_qr_is_valid_png_with_quiet_zone(self):
        from PIL import Image
        with self.request("/qr.png") as response:
            png = response.read()
        image = Image.open(io.BytesIO(png))
        image.load()
        self.assertEqual("PNG", image.format)
        self.assertEqual(image.width, image.height)
        self.assertEqual(255, image.getpixel((0, 0)))
        self.assertEqual(0, image.getpixel((45, 45)))

    def test_changed_notes_are_loaded_without_restarting_server(self):
        notes = self.root / "tools/update-notes.json"
        original = notes.read_bytes()
        try:
            notes.write_text(json.dumps({"0.11.1": ["新的更新说明"]}), encoding="utf-8")
            with self.request("/latest.json") as response:
                self.assertEqual(["新的更新说明"], json.load(response)["releaseNotes"])
        finally:
            notes.write_bytes(original)

    def test_invalid_notes_index_returns_unavailable_instead_of_invalid_metadata(self):
        notes = self.root / "tools/update-notes.json"
        original = notes.read_bytes()
        try:
            notes.write_text("[]", encoding="utf-8")
            with self.assertRaises(urllib.error.HTTPError) as context:
                self.request("/latest.json")
            self.assertEqual(503, context.exception.code)
        finally:
            notes.write_bytes(original)

    def test_public_and_wildcard_binds_are_rejected(self):
        for value in ["0.0.0.0", "8.8.8.8", "::"]:
            with self.assertRaises(ValueError):
                valid_bind(value)

    def test_running_server_picks_up_a_published_new_version(self):
        build = self.root / "android/app/build.gradle.kts"
        original = build.read_bytes()
        try:
            new_apk = self.apk + b"new-release"
            (self.root / "Duolian-V1.2.apk").write_bytes(new_apk)
            build.write_text('versionName = "1.2"\nversionCode = 16\n')
            with self.request("/latest.json") as response:
                manifest = json.load(response)
                self.assertEqual("1.2", manifest["version"])
                self.assertEqual("V1.2", manifest["displayVersion"])
                self.assertEqual("Duolian-V1.2.apk", manifest["filename"])
            with self.request("/latest.apk") as response:
                self.assertEqual(new_apk, response.read())
                self.assertIn('filename="Duolian-V1.2.apk"', response.headers["Content-Disposition"])
            with self.request("/") as response:
                self.assertIn("独练 V1.2", response.read().decode("utf-8"))
        finally:
            build.write_bytes(original)

    def test_preparing_release_does_not_silently_serve_an_old_apk(self):
        build = self.root / "android/app/build.gradle.kts"
        original = build.read_bytes()
        try:
            build.write_text('versionName = "0.99.0"\nversionCode = 99\n')
            with self.assertRaises(urllib.error.HTTPError) as context:
                self.request("/latest.apk")
            self.assertEqual(503, context.exception.code)
        finally:
            build.write_bytes(original)

    def test_chinese_filename_round_trips_in_manifest_headers_and_range(self):
        build = self.root / "android/app/build.gradle.kts"
        original = build.read_bytes()
        try:
            # A legacy alias must not override the canonical Chinese release.
            (self.root / "Duolian-V1.9.apk").write_bytes(b"old-alias")
            (self.root / "独练-V1.9.apk").write_bytes(self.apk)
            build.write_text('versionName = "1.9"\nversionCode = 23\n')
            with self.request("/latest.json") as response:
                manifest = json.load(response)
                self.assertEqual("独练-V1.9.apk", manifest["filename"])
                self.assertEqual(hashlib.sha256(self.apk).hexdigest(), manifest["sha256"])
            with self.request("/latest.apk", headers={"Range": "bytes=0-31"}) as response:
                self.assertEqual(206, response.status)
                self.assertEqual(self.apk[:32], response.read())
                self.assertIn("filename*=UTF-8''" + quote("独练-V1.9.apk"), response.headers["Content-Disposition"])
                self.assertIn('filename="Dulian-V1.9.apk"', response.headers["Content-Disposition"])
        finally:
            build.write_bytes(original)


if __name__ == "__main__":
    unittest.main()
