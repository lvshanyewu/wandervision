"""Host-side packaging smoke check; not a real-device inference test."""
import hashlib
from pathlib import Path
import sys
import zipfile

apk = Path(sys.argv[1])
with zipfile.ZipFile(apk) as z:
    names = set(z.namelist())
    assert z.testzip() is None, 'Corrupt ZIP entry'
    for name in ['AndroidManifest.xml', 'classes.dex', 'lib/arm64-v8a/libyolov8ncnn.so',
                 'assets/yolov8n.ncnn.param', 'assets/yolov8n.ncnn.bin']:
        assert name in names, 'Missing ' + name
        assert z.getinfo(name).file_size > 0
    assert {n.split('/')[1] for n in names if n.startswith('lib/')} == {'arm64-v8a'}
    expected_models={f'assets/{model}.ncnn.{ext}' for model in ('yolov8n','yolov8s','rdd2022-yolov8n','rdd2022-yolov8s') for ext in ('param','bin')}
    import json
    root=Path(__file__).resolve().parents[1]
    extra_assets={}
    for item in json.loads((root/'docs/experiment-models-manifest.json').read_text(encoding='utf-8')):
        extra_assets.update(item['assets'])
    extra_assets.update(json.loads((root/'docs/ocr-model-manifest.json').read_text(encoding='utf-8'))['assets'])
    for name,digest in extra_assets.items():
        assert hashlib.sha256(z.read('assets/'+name)).hexdigest()==digest,name
        if '.ncnn.' in name:expected_models.add('assets/'+name)
    assert {n for n in names if n.startswith('assets/') and '.ncnn.' in n} == expected_models
    import json
    manifest = json.loads((Path(__file__).resolve().parents[1] / 'docs/road-models-manifest.json').read_text())
    for model in manifest['models']:
        for name, digest in model['files'].items():
            assert hashlib.sha256(z.read('assets/'+name)).hexdigest() == digest, 'Model integrity: '+name
    elf = z.read('lib/arm64-v8a/libyolov8ncnn.so')
    assert elf[:5] == b'\x7fELF\x02' and int.from_bytes(elf[18:20], 'little') == 183, 'Not AArch64 ELF'
print('PASS: ARM64 ELF, manifest, dex, 10 pinned detectors + OCR/dictionary')
print('SHA256', hashlib.file_digest(apk.open('rb'), 'sha256').hexdigest())
