"""Verify anonymous HTTPS access to the actual published page, manifest, QR and APK."""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

from serve_download import ROOT, qr_png


def check(base, expected_apk):
    base = base.rstrip('/')
    origin = urlsplit(base)
    assert origin.scheme == 'https' and origin.hostname and not origin.path
    user_agent = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36'
    def fetch(url):
        # No login token, cookie, authorization header or private-site bypass is used.
        return urlopen(Request(url, headers={'User-Agent': user_agent}), timeout=45)
    with fetch(base + '/') as response:
        assert response.status == 200 and response.geturl().rstrip('/') == base
        page = response.read().decode('utf-8')
        assert '下载 Android 安装包' in page and '在浏览器打开' in page
    with fetch(base + '/latest.json') as response:
        assert response.status == 200 and response.geturl() == base + '/latest.json'
        assert 'application/json' in response.headers.get('Content-Type', '')
        release = json.load(response)
    assert release['packageName'] == 'app.duolian.local'
    download_url = release['downloadUrl']
    target = urlsplit(download_url)
    assert (target.scheme, target.netloc) == (origin.scheme, origin.netloc) and target.path.endswith('.apk')
    digest = hashlib.sha256()
    size = 0
    with fetch(download_url) as response:
        assert response.status == 200 and response.geturl() == download_url
        apk_type = response.headers.get('Content-Type', '')
        # Some static hosts return APKs as generic binary files. The URL and download filename
        # still end in .apk; the native updater supplies the APK MIME explicitly to FileProvider.
        assert apk_type.split(';', 1)[0] in ('application/vnd.android.package-archive', 'application/octet-stream'), apk_type
        while True:
            block = response.read(65536)
            if not block:
                break
            size += len(block)
            assert size <= release['size']
            digest.update(block)
    assert size == release['size'] and digest.hexdigest() == release['sha256']
    assert expected_apk.stat().st_size == size and hashlib.sha256(expected_apk.read_bytes()).hexdigest() == digest.hexdigest()
    with fetch(base + '/qr.png') as response:
        assert response.status == 200
        assert response.read() == qr_png(base + '/')
    result = dict(pageUrl=base + '/', downloadUrl=download_url, version=release['version'],
        versionCode=release['versionCode'], size=size, sha256=digest.hexdigest(),
        anonymousAccess=True, loginRequired=False, apkContentType=apk_type,
        releaseNotes=release['releaseNotes'], qrMatches=True, releaseByteEqual=True)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    result = check(args.base_url, args.apk)
    if args.report:
        args.report.write_bytes((json.dumps(result, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))
