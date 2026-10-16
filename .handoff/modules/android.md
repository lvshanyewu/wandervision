---
schema_version: 1
type: "module"
id: "android"
title: "Android 本地视觉与构建"
summary: "首版复用上游 NCNN YOLOv8，ARM64 通用物体及车辆测试。"
aliases: []
tags: ["Android", "NCNN"]
sources: [".handoff/overview.md"]
dependencies: []
related_modules: []
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-16T21:01:07+08:00"
---

# 当前范围
复用 nihui/ncnn-android-yolov8 的摄像头、预处理和检测链路。首版至少提供 COCO 通用模型，包含车辆和室内物体。
# 环境
Android Studio D:/AndroidStudio；SDK D:/AndroidStudioSDK；JBR 21；ADB MCP D:/Androiddevedoc/adb-mcp/adb-mcp.exe；当前无 ADB 设备。
# 检查边界
构建、APK 签名、ABI、模型打包与可执行冒烟；摄像头、推理效果、性能及长时间稳定性由用户手机验证。
