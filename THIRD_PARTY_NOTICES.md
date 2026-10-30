# Third-party sources and notices

## Web dashboard: Leaflet 1.9.4

https://github.com/Leaflet/Leaflet/releases/tag/v1.9.4 — BSD-2-Clause. Self-hosted unmodified JS/CSS in `web/static/vendor/leaflet/`, with upstream `LICENSE`. Official runtime hashes are checked by `tools/prepare_web_maps.py`; bundled hashes recorded in `web/static/data/asset-checksums.json`. No React/Vue or remote runtime CDN is required. The dashboard uses custom SVG marker icons; the unused Leaflet default marker images are not referenced.

## Chongqing OpenStreetMap data and OSRM route snapshots

© OpenStreetMap contributors, https://www.openstreetmap.org/copyright . The derived map database and route geometry in `web/static/data/` retain ODbL 1.0, https://opendatacommons.org/licenses/odbl/1-0/ . Source, query, limitations and refresh instructions are provided in `web/static/data/SOURCES.md` and `tools/prepare_web_maps.py`. These data licenses are separate from the AGPL-3.0 code. Attribution is visible on the interactive map. Optional live OSM raster tiles are requested only for the current viewport and are not redistributed with this repository.

OSRM route geometries were obtained once through the public demo service (non-commercial course project); no OSRM server or routing engine code is bundled. See https://project-osrm.org/docs/v5.24.0/api/ .

## ncnn-android-yolov8 demo source

Source: https://github.com/nihui/ncnn-android-yolov8/tree/f1ac75ec54ccb3817a9eba620fe51da8bdcf87ca

Imported Java, resources, Gradle wrapper, Camera2 NDK camera helper and YOLOv8 detector. The source files carry Tencent BSD 3-Clause notices; retained in each derived file. Modifications: independent application ID, reduced model/task selection, vehicle class display filter, lifecycle/permission handling, model load error propagation, ARM64-only build and CI.

## NCNN 20260526

https://github.com/Tencent/ncnn/releases/tag/20260526 — BSD 3-Clause. Linked from the official Android Vulkan package. Distribution copyright and license in `licenses/ncnn-BSD-3-Clause.txt`. Dependency package SHA-256 is pinned in `tools/prepare_native.py`.

## OpenCV Mobile 4.13.0, release v36

https://github.com/nihui/opencv-mobile/releases/tag/v36 — OpenCV Apache 2.0, with third-party components under their respective licenses. See `licenses/opencv-LICENSE.txt` and `licenses/opencv-mobile-LICENSE.txt`. Only core/imgproc are linked. Official binary package and checksum are pinned.

## YOLOv8 Nano and Small COCO models

Model files `yolov8n.ncnn.param`, `yolov8n.ncnn.bin`, `yolov8s.ncnn.param` and `yolov8s.ncnn.bin` are copied without conversion from the pinned nihui demo. YOLOv8 originates from https://github.com/ultralytics/ultralytics under AGPL-3.0 (or a separately obtained enterprise license). This repository and test APK are distributed as AGPL-3.0 with corresponding source available in this repository. No enterprise license is asserted. These are general COCO models, not RDD2022 damage detectors. Small weights SHA-256: c960a334aea8fae5f109f41c0ab2e93d374769a59f3bad61ab4412dad959ee2b.

## Gradle wrapper

https://github.com/gradle/gradle — Apache 2.0; wrapper source notices are retained. Full Apache license included in `licenses/Apache-2.0.txt`.
