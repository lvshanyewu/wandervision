"""Export checksum-verified standard YOLO candidates to dynamic raw DFL NCNN.

Use the isolated environment described by export_road_models.py. No training.
"""
import hashlib
import json
from pathlib import Path
import subprocess
import types
import torch
from ultralytics import YOLO
from export_road_models import raw_head

ROOT = Path(__file__).resolve().parents[1]
torch.set_num_threads(4)
manifest = []
for document in ('traffic-sign-model-candidates.json', 'teacher-scope-model-evidence.json'):
    data = json.loads((ROOT/'docs'/document).read_text(encoding='utf-8'))
    manifest.extend(data.get('candidates', data.get('models', [])))

if __name__ == '__main__':
    target = ROOT/'.deps/experiments'
    target.mkdir(exist_ok=True)
    catalog = []
    for item in manifest:
        # A public file without a stated license is research material, not bundled.
        if item.get('license_card', 'known') is None:
            continue
        cache_name = {'dronefreak/bdd100k-yolov8n':'bdd100k',
                      'ankitjha07/Traffic-Sign-Detection-YOLOv8':'signs36',
                      'yahyagul/traffic-sign-yolov8':'signs21',
                      'joker5914/yolov8n-license-plate':'plate',
                      'ShihoAI/yolov8s-garbage-detection':'garbage-v8',
                      'fabiocigaina/TACO-yolo11s':'litter-v11'}[item['repo']]
        folder = 'traffic-signs' if cache_name in ('bdd100k','signs36','signs21','plate') else 'teacher-scope'
        source = ROOT/f'.deps/research/{folder}/{cache_name}.pt'
        assert hashlib.sha256(source.read_bytes()).hexdigest() == item['sha256']
        model = YOLO(str(source)).model.float().eval().fuse()
        names = list(model.names.values())
        assert names == item['labels'], (cache_name,names)
        head = model.model[-1]
        assert head.reg_max == 16 and list(head.stride.tolist()) == [8,16,32]
        head.forward = types.MethodType(raw_head, head)
        path = target/(cache_name+'.torchscript')
        with torch.inference_mode():
            traced = torch.jit.trace(model,torch.zeros(1,3,640,640))
            traced.save(str(path))
        subprocess.run(['pnnx',str(path),'inputshape=[1,3,640,640]',
                        'inputshape2=[1,3,320,320]','fp16=1'],check=True)
        record = dict(item, key=cache_name, labels=names, assets={})
        for extension in ('param','bin'):
            asset = target/f'{cache_name.replace("-","_")}.ncnn.{extension}'
            record['assets'][asset.name] = hashlib.sha256(asset.read_bytes()).hexdigest()
        catalog.append(record)
        (target/'catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2),encoding='utf-8')
        print('EXPORTED',cache_name,flush=True)
