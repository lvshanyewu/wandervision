"""Install checksum-pinned RDD2022 NCNN models; no pickle or exporter runs in CI."""
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUNDLE_SHA256 = '12e82f49cff5a0d4753001bcb2bcfafbd9e234e750f156db5d790b658f513c3d'
URL = 'https://github.com/lvshanyewu/wandervision/releases/download/models-rdd2022-v1/rdd2022-ncnn-v1.zip'
cache = ROOT / '.deps/rdd2022-ncnn-v1.zip'
cache.parent.mkdir(exist_ok=True)
if not cache.exists():
    urllib.request.urlretrieve(URL, cache)
assert hashlib.file_digest(cache.open('rb'), 'sha256').hexdigest() == BUNDLE_SHA256, 'Model bundle checksum mismatch'
manifest = json.loads((ROOT / 'docs/road-models-manifest.json').read_text(encoding='utf-8'))
with zipfile.ZipFile(cache) as z:
    for model in manifest['models']:
        for name, digest in model['files'].items():
            assert name in {f'rdd2022-yolov8{s}.ncnn.{e}' for s in ('n','s') for e in ('param','bin')}
            data = z.read(name)
            assert hashlib.sha256(data).hexdigest() == digest, name
            (ROOT / 'android/app/src/main/assets' / name).write_bytes(data)
print('PASS: pinned RDD2022 Nano + Small model bundle')
