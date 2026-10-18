"""Download checksum-pinned upstream Android libraries (standard library only)."""
import argparse
import hashlib
from pathlib import Path
import urllib.request
import zipfile

DEPS = [
    ('ncnn.zip', 'https://github.com/Tencent/ncnn/releases/download/20260526/ncnn-20260526-android-vulkan.zip', '26909c92eed35afed4a966b5e9e503fcb0a529691ea3f910ec2c94a4fff52804'),
    ('opencv.zip', 'https://github.com/nihui/opencv-mobile/releases/download/v36/opencv-mobile-4.13.0-android.zip', '56735469e0c344253122cbc66f5e69e5f3b2c131a0f892eb68fa5530be763b83'),
]
parser = argparse.ArgumentParser()
parser.add_argument('--cache', type=Path, default=Path(__file__).resolve().parents[1] / '.deps')
args = parser.parse_args()
args.cache.mkdir(parents=True, exist_ok=True)
target = Path(__file__).resolve().parents[1] / 'android/app/src/main/jni'
for name, url, checksum in DEPS:
    archive = args.cache / name
    if not archive.exists():
        print('Downloading', url, flush=True)
        urllib.request.urlretrieve(url, archive)
    if hashlib.file_digest(archive.open('rb'), 'sha256').hexdigest() != checksum:
        raise SystemExit('Checksum mismatch: ' + str(archive))
    with zipfile.ZipFile(archive) as z:
        z.extractall(target)
    print('Verified and extracted', name, flush=True)

import runpy
runpy.run_path(str(Path(__file__).with_name('prepare_models.py')), run_name='__main__')
