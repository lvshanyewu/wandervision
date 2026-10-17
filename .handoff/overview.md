---
schema_version: 1
type: "overview"
id: "wandervision"
title: "wandervision 道路智能巡检"
summary: "道路巡检课设：Android 0.3.0 NCNN 本地视觉已交付；独立 Web 重庆模拟监控中心已开发并部署，下一阶段对接 Android GPS/事件。"
aliases: []
tags: []
updated_at: "2026-10-17T09:20:00+08:00"
---

# 项目目标

wandervision：复用成熟 NCNN Android YOLOv8 链路，完成可运行、可演示的道路智能巡检系统。Android 0.3.0 已交付；2026-10-17 用户授权先独立完成 Web Demo，暂不接真实 Android。

# 导航

- [阶段路线与任务](modules/roadmap.md)
- [Android 本地推理与构建](modules/android.md)
- [Web 监控中心架构与接口](modules/web-dashboard.md)
- [Web 首版开发/测试/部署结果](continuations/web-dashboard-v1.md)
- [Web 启动、部署与回退](../docs/web-dashboard.md)
- [首个 APK 当前进度](continuations/first-apk.md)
- [构建与使用](../README.md)
- [真机测试说明](../docs/releases/0.1.0.md)

# 工作边界

独立 Web 采用明确标注的模拟数据并预留真实 API；Android 本次不修改，道路病害模型、GPS 与视频接入仍待后续。保留第三方来源和许可。电脑端检查不能证明真机相机、推理质量、性能和稳定性已通过；阶段进展和发布结果持续同步 Vervision。
