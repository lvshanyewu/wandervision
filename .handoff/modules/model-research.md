---
schema_version: 1
type: "module"
id: "model-research"
title: "路面预训练模型与Turnip接入调查"
summary: "优先RDD2022 YOLOv8n，再对照Small；需NCNN原始DFL输出适配。Turnip存在Android命名空间与加载句柄桥接要求，未集成。"
aliases: []
tags: []
sources: ["docs/road-models-and-turnip.md", "android/app/src/main/jni/yolov8_det.cpp", "android/app/src/main/jni/CMakeLists.txt"]
dependencies: []
related_modules: ["android"]
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-17T09:32:08+08:00"
---

# 研究结论

2026-10-17已检查HF RDD2022 YOLOv8n/s，四类裂缝/坑洞；Nano权重固定revision下载并静态检查Detect/DFL与类别，未执行pickle或加入APK。现有解码依赖64列DFL+类别logits，普通NCNN导出可能为已解码输出，必须转换并对比，而非换文件名。保留COCO Nano/Small。YOLOP可作后续可行驶区/车道线，需分割分支与独立后处理；坑洞专项候选有访问条件，不作为首选。

Turnip是Vulkan GPU驱动，不是NPU引擎。NCNN20260526提供driver_path且SIMPLEVK启用，但静态检查K11MCH1 R8 ARM64 HAL驱动发现受限平台库依赖。libadrenotools需要解包原生库与命名空间hooks，返回句柄不能直接传现有NCNN路径API。未替换系统驱动或添加假Turnip开关。后续隔离桥接、固定依赖许可/哈希、核对实际driverID、CPU恢复与真机对照测试；不保证更快。

# 证据与边界

版本/源链接、下载哈希和ELF依赖详见docs/road-models-and-turnip.md；本机研究产物位于忽略的.deps目录，不提交或分发。没有宣称病害模型NCNN导出或Turnip加载/推理成功。
