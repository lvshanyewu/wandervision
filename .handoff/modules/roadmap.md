---
schema_version: 1
type: "module"
id: "roadmap"
title: "目标与分阶段路线"
summary: "本地视觉优先的道路巡检课设；已交付首个APK，当前迭代专业相机UI与双模型，后续病害/GPS/同步/Web。"
aliases: []
tags: ["规划", "MVP"]
sources: ["README.md", "AGENTS.md", "docs/releases/0.1.0.md"]
dependencies: []
related_modules: []
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-16T22:27:29+08:00"
---

# 目标
Android 本地视觉推理、GPS 巡检事件；低配云接收存储查询；Web 地图图片与统计。完成可运行演示系统。
# 阶段与任务

- P0 已交付：环境/仓库初始化、NCNN Nano ARM64 APK、CI/Release、用户初步真机反馈。
- P0.2 当前：专业相机/手持仪器外观；小米13横竖屏；Nano保留+Small比较；真实HUD/仪表盘、截图记录/系统分享；再次真机验收。
- P1：RDD2022 YOLOv8n兼容核对、NCNN病害模型；体积和性能调优、真实样本测试。
- P2：GPS、检测事件去重/本地数据库、截图元数据与巡检工作流；继续完善界面。
- P3：轻量后端/数据库/图片存储、离线重试与事件同步。
- P4：Web地图/筛选/统计；测试指标与课程设计、演示材料。
- 可选：车牌、道路遗留物检测。

# 原则
复用成熟组件；本地推理；小步交付；保留许可证；电脑构建检查与真机效果验证分开。P1 之后目前仅规划。
