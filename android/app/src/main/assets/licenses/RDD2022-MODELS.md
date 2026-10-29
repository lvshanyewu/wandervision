# RDD2022 YOLOv8 road-damage models

Publisher: dronefreak / DetectionBench
Sources: https://huggingface.co/dronefreak/rdd2022-yolov8n and https://huggingface.co/dronefreak/rdd2022-yolov8s
License declared by publisher: AGPL-3.0 (full text in LICENSE).
Nano revision: acf216a43d796f7910fc854f662c1e9cfa03fc01
Small revision: 06d1dbb6cb1b30f54a5f32f9fd9ee29695e1b106

Converted by wandervision using tools/export_road_models.py: fused convolutions, raw 64 DFL regression logits plus 4 class logits, dynamic input, FP16 stored weights. Android decodes the heads. Corresponding export code, hashes and validation are published in the source repository: https://github.com/lvshanyewu/wandervision

Labels in order: longitudinal_crack, transverse_crack, alligator_crack, pothole. These models do not detect every kind of road hazard. Nano/Small are independent choices, not combined inference. No phone accuracy or speed is claimed before user testing.

Dataset attribution: RDD2022, Arya, Maeda, Ghosh, Toshniwal and Sekimoto, 2022, arXiv:2209.08538. Dataset images are CC BY-SA 4.0 per publisher; no dataset images are redistributed in this APK.
Ultralytics YOLOv8: https://github.com/ultralytics/ultralytics (AGPL-3.0).
