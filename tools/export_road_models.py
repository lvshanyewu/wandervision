"""Reproduce raw-DFL dynamic NCNN exports from checksum-pinned RDD2022 weights.

Run with torch 2.14.1, ultralytics 8.4.181, pnnx 20260526 (isolated environment).
The trusted publisher's PyTorch checkpoint is loaded only after hash verification.
"""
import hashlib
from pathlib import Path
import subprocess
import types
import torch
from ultralytics import YOLO

ROOT = Path(__file__).resolve().parents[1]
WEIGHTS = {
    'n': 'd1c3536d8e4a873b7ff251d1cb81c0047100f06c86f92e08596de2d618618a3e',
    's': '94945fc2d4fe22f6c846109030e90411d342fafd86e63c7ac0aa396e364dfc55',
}

def raw_head(self, x):
    # Same trained convolutions, no anchors/DFL/sigmoid: Android handles these.
    return torch.cat([torch.cat((self.cv2[i](x[i]), self.cv3[i](x[i])), 1)
                      .flatten(2).transpose(1, 2) for i in range(self.nl)], 1)

if __name__ == '__main__':
    torch.set_num_threads(4)
    for size, digest in WEIGHTS.items():
        weights = ROOT / f'.deps/research/rdd2022-yolov8{size}.pt'
        assert hashlib.sha256(weights.read_bytes()).hexdigest() == digest
        model = YOLO(str(weights)).model.float().eval().fuse()
        assert list(model.names.values()) == ['longitudinal_crack', 'transverse_crack', 'alligator_crack', 'pothole']
        head = model.model[-1]
        assert head.reg_max == 16 and head.nc == 4
        head.forward = types.MethodType(raw_head, head)
        output = ROOT / f'.deps/research/rdd2022-yolov8{size}.torchscript'
        with torch.inference_mode():
            traced = torch.jit.trace(model, torch.zeros(1, 3, 640, 640))
            traced.save(str(output))
        subprocess.run(['pnnx', str(output), 'inputshape=[1,3,640,640]',
                        'inputshape2=[1,3,320,320]', 'fp16=1'], check=True)
