"""Compare the raw exported heads against TorchScript, including rectangular inputs."""
import json
from pathlib import Path
import ncnn
import numpy as np
import torch

root = Path(__file__).resolve().parents[1]
torch.set_num_threads(4)
checks = []
for size in ('n', 's'):
    model = torch.jit.load(str(root / f'.deps/research/rdd2022-yolov8{size}.torchscript')).eval()
    with ncnn.Net() as net:
        net.opt.num_threads = 4
        assert net.load_param(str(root / f'.deps/research/rdd2022_yolov8{size}.ncnn.param')) == 0
        assert net.load_model(str(root / f'.deps/research/rdd2022_yolov8{size}.ncnn.bin')) == 0
        for h, w in ((320,320),(480,480),(640,640),(320,480),(640,384)):
            x = np.random.default_rng(42).random((1,3,h,w),dtype=np.float32)
            with torch.inference_mode(): expected = model(torch.from_numpy(x)).numpy()[0]
            with net.create_extractor() as ex:
                assert ex.input('in0',ncnn.Mat(x[0]).clone()) == 0
                status, output = ex.extract('out0')
                actual = np.array(output)
            assert status == 0 and actual.shape == expected.shape
            assert expected.shape == (sum(h//s*(w//s) for s in (8,16,32)),68)
            maximum = float(np.max(np.abs(actual-expected)))
            mean = float(np.mean(np.abs(actual-expected)))
            assert np.isfinite(actual).all() and maximum < .25 and mean < .02, (size,h,w,maximum,mean)
            checks.append(dict(model=size,height=h,width=w,shape=list(actual.shape),max_abs=maximum,mean_abs=mean))
            print(checks[-1],flush=True)
(root/'artifacts/road-export-verification.json').write_text(json.dumps(checks,indent=2))
