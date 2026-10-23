"""Generate a static HTTPS download site containing only release files and public help."""
import argparse
import hashlib
import html
import json
import re
import shutil
import subprocess
from pathlib import Path
from urllib.parse import quote, urlsplit

from serve_download import ROOT, qr_png


def build(base_url, apk, output, aapt2):
    base_url = base_url.rstrip('/')
    url = urlsplit(base_url)
    if url.scheme != 'https' or not url.hostname or url.username or url.query or url.fragment or url.path:
        raise ValueError('Use an HTTPS site origin without a path or credentials')
    config = (ROOT / 'android/app/build.gradle.kts').read_text(encoding='utf-8-sig')
    version = re.search(r'versionName\s*=\s*"([0-9.]+)"', config).group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', config).group(1))
    apk = apk or next((p for p in [ROOT / f'独练-V{version}.apk', ROOT / f'Duolian-V{version}.apk'] if p.is_file()), ROOT / f'独练-V{version}.apk')
    badging = subprocess.run([str(aapt2), 'dump', 'badging', str(apk)], check=True, capture_output=True).stdout.decode('utf-8', 'replace')
    if f"name='app.duolian.local' versionCode='{code}' versionName='{version}'" not in badging:
        raise ValueError('APK does not match the configured release')
    size = apk.stat().st_size
    if not 0 < size < 25 * 1024 * 1024:
        raise ValueError('The static hosting asset must be under 25 MiB')
    sha = hashlib.sha256(apk.read_bytes()).hexdigest()
    notes = json.loads((ROOT / 'tools/update-notes.json').read_text(encoding='utf-8'))[version]
    filename = f'独练-V{version}.apk'
    encoded_filename = quote(filename, safe='')
    release = dict(version=version, displayVersion='V' + version, versionCode=code,
        packageName='app.duolian.local', minSdk=26, size=size, sha256=sha, filename=filename,
        downloadUrl=f'{base_url}/releases/{encoded_filename}', pageUrl=base_url + '/', releaseNotes=notes)
    output.mkdir(parents=True, exist_ok=True)
    (output / 'releases').mkdir(exist_ok=True)
    shutil.copy2(apk, output / 'releases' / filename)
    (output / 'latest.json').write_bytes((json.dumps(release, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))
    (output / 'qr.png').write_bytes(qr_png(base_url + '/'))
    template = ROOT / 'tools/public-download-template'
    replacements = {'__VERSION__': 'V' + version, '__SIZE__': f'{size / 1024 / 1024:.1f}',
        '__BASE_URL__': html.escape(base_url), '__APK_PATH__': f'releases/{encoded_filename}',
        '__SHA__': sha, '__NOTES__': ''.join('<li>' + html.escape(n) + '</li>' for n in notes)}
    for name in ['index.html', 'privacy.html', 'style.css', 'app.js', '_headers', 'robots.txt']:
        text = (template / name).read_text(encoding='utf-8')
        for key, value in replacements.items():
            text = text.replace(key, value)
        (output / name).write_bytes(text.encode('utf-8'))
    image = ROOT / 'android/app/src/main/res/drawable-nodpi/training_daylight.webp'
    shutil.copy2(image, output / 'training.webp')
    print(json.dumps({'version': version, 'versionCode': code, 'size': size, 'sha256': sha,
        'pageUrl': base_url + '/', 'downloadUrl': release['downloadUrl'], 'output': str(output)}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'public-download-site/dist')
    parser.add_argument('--aapt2', type=Path, default=Path('C:/tmp/fitness-sdk/build-tools/35.0.0/aapt2.exe'))
    args = parser.parse_args()
    build(args.base_url, args.apk, args.output, args.aapt2)
