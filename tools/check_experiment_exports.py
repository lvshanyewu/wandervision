"""Host numeric checks for new model heads (not a road accuracy benchmark)."""
import json
from pathlib import Path
import torch
import numpy as np
import ncnn

ROOT=Path(__file__).resolve().parents[1]
torch.set_num_threads(4)
results=[]
for item in json.loads((ROOT/'.deps/experiments/catalog.json').read_text(encoding='utf-8')):
    key=item['key'];prefix=ROOT/'.deps/experiments'/key.replace('-','_')
    traced=torch.jit.load(str(ROOT/'.deps/experiments'/(key+'.torchscript'))).eval()
    with ncnn.Net() as net:
        net.opt.num_threads=4
        assert net.load_param(str(prefix)+'.ncnn.param')==0
        assert net.load_model(str(prefix)+'.ncnn.bin')==0
        for h,w in ((320,320),(480,320),(640,640)):
            x=np.random.default_rng(42).random((1,3,h,w),dtype=np.float32)
            with torch.inference_mode():expected=traced(torch.from_numpy(x)).numpy()[0]
            with net.create_extractor() as ex:
                assert ex.input('in0',ncnn.Mat(x[0]).clone())==0
                status,out=ex.extract('out0');actual=np.array(out)
            assert status==0 and actual.shape==expected.shape
            assert actual.shape==(sum(h//s*(w//s) for s in (8,16,32)),64+len(item['labels']))
            difference=np.abs(actual-expected);maximum=float(difference.max());mean=float(difference.mean())
            assert np.isfinite(actual).all()
            def sigmoid(v): return 1/(1+np.exp(-np.clip(v,-80,80)))
            score_error=float(np.abs(sigmoid(actual[:,64:])-sigmoid(expected[:,64:])).max())
            def distances(v):
                v=v[:,:64].reshape(-1,4,16);v=np.exp(v-v.max(2,keepdims=True));v/=v.sum(2,keepdims=True)
                return (v*np.arange(16)).sum(2)
            strides=np.concatenate([np.full(h//v*(w//v),v) for v in (8,16,32)])
            distance_error=np.abs(distances(actual)-distances(expected))*strides[:,None]
            mask=np.maximum(sigmoid(actual[:,64:]).max(1),sigmoid(expected[:,64:]).max(1))>=.25
            box_error=float(distance_error[mask].max()) if mask.any() else 0
            mean_box_error=float(distance_error.mean())
            assert mean<.03 and score_error<.05 and box_error<2 and mean_box_error<.3,(key,maximum,mean,score_error,box_error)
            result=dict(model=key,height=h,width=w,shape=list(actual.shape),max_abs=maximum,mean_abs=mean,max_score_error=score_error,max_box_distance_error_px=box_error,mean_box_error_all_px=mean_box_error)
            results.append(result);print(result,flush=True)
(ROOT/'artifacts/experiment-export-checks.json').write_text(json.dumps(results,indent=2))
