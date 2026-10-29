"""Validate a real exported 3.1 inspection record and its JPEG (standard library only)."""
import argparse
import datetime
import hashlib
import json
import math
from pathlib import Path
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('record', type=Path)
parser.add_argument('image', type=Path)
args = parser.parse_args()
j = json.loads(args.record.read_text(encoding='utf-8'))
assert j['schema_version'] == '1.0'
assert str(uuid.UUID(j['event_id'])) == j['event_id']
assert j['captured_at'].endswith('Z')
stamp = datetime.datetime.fromisoformat(j['captured_at'].replace('Z', '+00:00'))
assert abs(stamp.timestamp() * 1000 - j['captured_at_epoch_ms']) < 1
assert j['capture_elapsed_ns'] > 0
camera, inf, loc, image = (j[k] for k in ('camera', 'inference', 'location', 'image'))
assert camera['frame_id'] > 0 and camera['width_px'] > 0 and camera['height_px'] > 0
assert camera['source'] == 'native_viewfinder_frame'
assert inf['result_alignment'] == 'same_native_frame'
assert j['objects'] == len(inf['detections'])
if not inf['enabled']:
    assert not inf['detections'] and inf['inference_ms'] is None and inf['max_confidence'] is None
for detection in inf['detections']:
    road = inf['model_id'] in {'rdd2022-yolov8n','rdd2022-yolov8s'}
    extra_counts={'bdd100k-yolov8n':10,'traffic-signs36-yolov8':36,'traffic-signs21-yolov8':21,'plate-yolov8n':1,'garbage-yolov8s':6,'litter-yolo11s':9}
    assert 0 <= detection['class_id'] < (4 if road else extra_counts.get(inf['model_id'],80)) and detection['label']
    if road:
        assert detection['label'] == ['longitudinal_crack','transverse_crack','alligator_crack','pothole'][detection['class_id']]
    assert 0 <= detection['confidence'] <= 1
    box = detection['bbox_xywh_normalized']
    assert len(box) == 4 and all(math.isfinite(x) and 0 <= x <= 1 for x in box)
    assert box[0]+box[2] <= 1.00001 and box[1]+box[3] <= 1.00001
assert loc['coordinate_system'] == 'WGS84'
assert loc['status'] in {'off','permission_denied','provider_disabled','searching','unavailable','fresh','approximate','stale'}
if loc['status'] in {'fresh', 'approximate', 'stale'}:
    assert -90 <= loc['latitude'] <= 90 and -180 <= loc['longitude'] <= 180
    assert loc['accuracy_m'] > 0 and loc['age_ms'] >= 0 and isinstance(loc['mock'], bool)
    assert (loc['age_ms'] > 30000) == (loc['status'] == 'stale')
else:
    assert loc['latitude'] is None and loc['longitude'] is None
assert j['sync']['state'] == 'local_only' and j['sync']['attempts'] == 0
assert not image['contains_ui_controls'] and image['mime_type'] == 'image/jpeg'
assert image['width_px'] == camera['width_px'] and image['height_px'] > camera['height_px']
assert args.image.stat().st_size == image['bytes']
assert hashlib.file_digest(args.image.open('rb'), 'sha256').hexdigest() == image['sha256']
assert args.image.read_bytes()[:2] == b'\xff\xd8'
print('PASS: same-frame event, location status, detections, timestamps and JPEG integrity')
