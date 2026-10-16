# Third-party sources and notices

## ncnn-android-yolov8 demo source

Source: https://github.com/nihui/ncnn-android-yolov8/tree/f1ac75ec54ccb3817a9eba620fe51da8bdcf87ca

Imported Java, resources, Gradle wrapper, Camera2 NDK camera helper and YOLOv8 detector. The source files carry Tencent BSD 3-Clause notices; retained in each derived file. Modifications: independent application ID, reduced model/task selection, vehicle class display filter, lifecycle/permission handling, model load error propagation, ARM64-only build and CI.

## NCNN 20260526

https://github.com/Tencent/ncnn/releases/tag/20260526 — BSD 3-Clause. Linked from the official Android Vulkan package. Distribution copyright and license in `licenses/ncnn-BSD-3-Clause.txt`. Dependency package SHA-256 is pinned in `tools/prepare_native.py`.

## OpenCV Mobile 4.13.0, release v36

https://github.com/nihui/opencv-mobile/releases/tag/v36 — OpenCV Apache 2.0, with third-party components under their respective licenses. See `licenses/opencv-LICENSE.txt` and `licenses/opencv-mobile-LICENSE.txt`. Only core/imgproc are linked. Official binary package and checksum are pinned.

## YOLOv8 Nano COCO model

Model files `yolov8n.ncnn.param` and `yolov8n.ncnn.bin` are copied without conversion from the pinned nihui demo. YOLOv8 originates from https://github.com/ultralytics/ultralytics under AGPL-3.0 (or a separately obtained enterprise license). This repository and test APK are distributed as AGPL-3.0 with corresponding source available in this repository. No enterprise license is asserted. The model is a general COCO model, not an RDD2022 damage detector.

## Gradle wrapper

https://github.com/gradle/gradle — Apache 2.0; wrapper source notices are retained. Full Apache license included in `licenses/Apache-2.0.txt`.
