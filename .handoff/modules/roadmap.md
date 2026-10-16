---
schema_version: 1
type: "module"
id: "roadmap"
title: "目标与分阶段路线"
summary: "本地视觉优先的轻量道路巡检课程设计；当前交付首个 APK。"
aliases: []
tags: ["规划", "MVP"]
sources: [".handoff/overview.md"]
dependencies: []
related_modules: []
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-16T21:01:07+08:00"
---

# 目标
Android 本地视觉推理、GPS 巡检事件；低配云接收存储查询；Web 地图图片与统计。完成可运行演示系统。
# 阶段与任务
- P0 当前：环境验证、仓库初始化、复用 NCNN YOLOv8、ARM64 通用/车辆检测 APK、构建冒烟、GitHub Release、用户真机验收。
- P1：RDD2022 YOLOv8n 兼容核对与 NCNN 转换；病害模式、体积与性能优化。
- P2：Material Design 3 巡检界面；GPS、事件、截图、本地数据库。
- P3：轻量后端、数据库、图片存储、离线同步。
- P4：Web 地图与事件统计；测试指标、课程设计文档、演示材料。
- 可选：车牌、遗留物检测。
# 原则
复用成熟组件；本地推理；小步交付；保留许可证；电脑构建检查与真机效果验证分开。P1 之后目前仅规划。
