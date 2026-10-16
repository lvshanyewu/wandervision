---
schema_version: 1
type: "overview"
id: "wandervision"
title: "wandervision 道路智能巡检"
summary: "轻量道路智能巡检课程设计：Android 本地视觉优先，先交付首个可安装 ARM64 APK，再迭代病害识别、GPS 事件、后端与 Web。"
aliases: []
tags: []
updated_at: "2026-10-16T21:00:15+08:00"
---

# 项目目标

wandervision：复用成熟 NCNN Android YOLOv8 链路，完成可运行、可演示的道路智能巡检系统。当前优先交付首个 ARM64 测试 APK，由用户在骁龙 8 Gen 2 真机验收。

# 导航

- [阶段路线与任务](modules/roadmap.md)
- [Android 本地推理与构建](modules/android.md)
- [首个 APK 当前进度](continuations/first-apk.md)
- [构建与使用](../README.md)
- [真机测试说明](../docs/releases/0.1.0.md)

# 工作边界

当前不开发道路病害移植、后端或 Web。保留第三方来源和许可。电脑端检查不能证明真机相机、推理质量、性能和稳定性已通过；阶段进展和发布结果持续同步 Vervision。
