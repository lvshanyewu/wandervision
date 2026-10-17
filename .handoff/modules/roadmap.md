---
schema_version: 1
type: "module"
id: "roadmap"
title: "目标与分阶段路线"
summary: "Android 0.3.0 已交付；2026-10-17 提前独立完成 Web 模拟监控中心，下一阶段接入 Android GPS/事件及病害模型。"
aliases: []
tags: ["规划", "MVP"]
sources: ["README.md", "AGENTS.md", "docs/releases/0.1.0.md"]
dependencies: []
related_modules: ["android", "web-dashboard"]
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-17T09:15:50+08:00"
---

# 目标
Android 本地视觉推理、GPS 巡检事件；低配云接收存储查询；Web 地图图片与统计。完成可运行演示系统。
# 阶段与任务

- P0 已交付：环境/仓库初始化、NCNN Nano ARM64 APK、CI/Release、用户初步真机反馈。
- Android 0.3.0 已交付黑灰黄巡检界面、Nano/Small、真实统计及截图分享；0.2.0 用户已确认功能正常，0.3.0 待进一步真机反馈。
- Web v1：2026-10-17 用户授权提前独立实施原生 Web 重庆仪表盘、10 台模拟设备+3个真实接入位、轻量 Python/SQLite 接口并复用 myseoul 部署；验收结果见 web-dashboard-v1 continuation。
- 下一阶段：Android GPS 权限/WGS84 定位、本地事件队列、独立令牌、心跳和幂等事件同步。
- 病害阶段：RDD2022 YOLOv8n/NCNN 兼容核对、模型体积/性能调优、真实样本与用户实机验证。
- 后续：可选低频预览/实时视频、人工处置、设备访问控制和课设材料。
- 可选：车牌、道路遗留物检测。

# 原则

复用成熟组件；本地推理；小步交付；保留许可证；电脑构建检查与真机效果验证分开。Web 模拟明确标注，真实设备初始待接入；不以 Demo 代替真实 Android 摄像头或推理验证。
