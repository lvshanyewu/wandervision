"""Install the separately published, checksum-pinned experiment models; no pickle execution."""
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[1]
SHA256='c5195d3e5352695513f14d7be9be039a8928fadd6428e5007ad0c97d4d02add6'
URL='https://github.com/lvshanyewu/wandervision/releases/download/models-experiments-v1/experiments-ncnn-v1.zip'
assets={}
for item in json.loads((ROOT/'docs/experiment-models-manifest.json').read_text(encoding='utf-8')):
    assets.update(item['assets'])
assets.update(json.loads((ROOT/'docs/ocr-model-manifest.json').read_text(encoding='utf-8'))['assets'])
cache=ROOT/'.deps/experiments-ncnn-v1.zip'
cache.parent.mkdir(exist_ok=True)
if not cache.exists():urllib.request.urlretrieve(URL,cache)
assert hashlib.file_digest(cache.open('rb'),'sha256').hexdigest()==SHA256,'Experiment bundle checksum mismatch'
with zipfile.ZipFile(cache) as z:
    for name,digest in assets.items():
        assert Path(name).name==name and ('..' not in name)
        data=z.read(name);assert hashlib.sha256(data).hexdigest()==digest,name
        (ROOT/'android/app/src/main/assets'/name).write_bytes(data)
print('PASS: pinned 6 experiment detectors + OCR model/dictionary')
