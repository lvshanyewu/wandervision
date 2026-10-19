# wandervision 3.4 experimental model sources

The original publishers and upstream licenses remain applicable. Converted NCNN weights are accompanied by fixed source revisions and SHA256 in docs/experiment-models-manifest.json and docs/ocr-model-manifest.json in https://github.com/lvshanyewu/wandervision . No training is claimed.

YOLO exports: trained convolution weights retained, convolution/BN fused, inference postprocess removed, dynamic raw 64 DFL logits plus model-local class logits, FP16 stored weights, input in0/output out0. Ultralytics YOLO: AGPL-3.0, full text LICENSE. Model-card MIT declarations do not remove upstream/data conditions.

- dronefreak/bdd100k-yolov8n: https://huggingface.co/dronefreak/bdd100k-yolov8n/tree/2c24162504d096e7bb0a12ec0af6bfeb9d5d697a
- ankitjha07/Traffic-Sign-Detection-YOLOv8: https://huggingface.co/ankitjha07/Traffic-Sign-Detection-YOLOv8/tree/fe61bd7b9c6c6558162baa882597c90692ef7bf9
- yahyagul/traffic-sign-yolov8: https://huggingface.co/yahyagul/traffic-sign-yolov8/tree/15be2681d3b6e666f45c744219aab84e97a2f1b4
- joker5914/yolov8n-license-plate: https://huggingface.co/joker5914/yolov8n-license-plate/tree/8286762929bd4b111a19186f2a05e0a5940b6088
- ShihoAI/yolov8s-garbage-detection: https://huggingface.co/ShihoAI/yolov8s-garbage-detection/tree/da4adf3c05daf7aa5700abbffcaf175cdef0cb35
- fabiocigaina/TACO-yolo11s: https://huggingface.co/fabiocigaina/TACO-yolo11s/tree/6ee8e6fd4e4804b8d5885d09cbe373a8befcc728

BDD100K and RDD2022 data attribution remains with the original dataset authors. TACO: Pedro F. Proença and Pedro Simões, Trash Annotations in Context, 2020. No training images are redistributed in this APK. Garbage models are limited experimental known-class detectors, not universal road anomaly detectors.

HyperLPR3: https://github.com/szad670401/HyperLPR commit9307450f7b7915be18f23a539ec05b41fe6629f4, Apache-2.0 (HyperLPR-Apache-2.0.txt). Only the published recognizer is converted ONNX -> pnnx/NCNN, FP16 stored weights. Preprocess and CTC decode adapted from Prj-Python/hyperlpr3/inference/recognition.py; dictionary from common/tokenize.py. Copyright retained by HyperLPR/HyperInspire contributors. Changes: YOLO bbox crops, NCNN CPU execution on capture, no MNN runtime, no separate HyperLPR detection/classifier/rectification, cap3 crops, reject unknown class77 because source dictionary has77 symbols and graph has78 outputs. This is not a claim of full upstream SDK equivalence.

Rule tracking is a small wandervision class-aware IoU/centroid implementation, no ReID or world-coordinate estimation. Fixed-view direction/ROI/dwell and virtual-line experiments are not speed/height/real-solid-line/illegal-parking certification.
