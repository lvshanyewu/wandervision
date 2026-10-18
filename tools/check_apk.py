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
    assert {n for n in names if n.startswith('assets/') and '.ncnn.' in n} == {
        'assets/yolov8n.ncnn.param', 'assets/yolov8n.ncnn.bin',
        'assets/yolov8s.ncnn.param', 'assets/yolov8s.ncnn.bin',
        'assets/rdd2022-yolov8n.ncnn.param', 'assets/rdd2022-yolov8n.ncnn.bin',
        'assets/rdd2022-yolov8s.ncnn.param', 'assets/rdd2022-yolov8s.ncnn.bin'}
    import json
    manifest = json.loads((Path(__file__).resolve().parents[1] / 'docs/road-models-manifest.json').read_text())
    for model in manifest['models']:
        for name, digest in model['files'].items():
            assert hashlib.sha256(z.read('assets/'+name)).hexdigest() == digest, 'Model integrity: '+name
    elf = z.read('lib/arm64-v8a/libyolov8ncnn.so')
    assert elf[:5] == b'\x7fELF\x02' and int.from_bytes(elf[18:20], 'little') == 183, 'Not AArch64 ELF'
print('PASS: ARM64 ELF, manifest, dex, COCO Nano/Small and pinned RDD2022 Nano/Small')
print('SHA256', hashlib.file_digest(apk.open('rb'), 'sha256').hexdigest())
