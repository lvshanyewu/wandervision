---
schema_version: 1
type: "module"
id: "roadmap"
title: "目标与分阶段路线"
summary: "课程数据中心v4与Android3.4.1已交付：校园前台、复核台账、筛选导出与报告；下一步用户真机上传及课程文档验收。"
aliases: []
tags: ["规划", "MVP"]
sources: ["README.md", "AGENTS.md", "docs/web-dashboard.md", "docs/android-monitor.md"]
dependencies: []
related_modules: ["android", "web-dashboard"]
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-29T15:57:29+08:00"
---

# 目标
Android本地视觉、定位巡检记录；轻量云接收图片和JSON；Web地图、设备与统计。优先完整、稳定、低资源课程演示。

# 阶段与任务

- Android基础具备NCNN Nano/Small ARM64、原生巡检UI、照片/完整JSON、WGS84定位、RDD模型与可选OCR/实验规则；真实质量以用户反馈为准。
- 校园Web已保留10模拟+3PHONE、五预设、Canvas合成循环、配对/照片与短TTL LIVE。
- 2026-10-29 Web v4实现精简展示首页及独立/admin/数据台账、人工复核与标记、筛选CSV/JSON导出、/report/自包含可视化报告，副标题“道路巡检具身智能体”。58项Web测试通过并部署，适配服务器换IP。
- Android3.4.1由原App对话发布，补充冻结采集点与备注，原照片与YOLO/模型/定位完整上报契约兼容。
- 下一步由用户验收：真实手机采集点/备注→拍照上传→后台目标与位置查看→复核/标记→筛选导出/报告。
- 报告母版修改交接见docs/course-report-v4-update.md；待用户验收再替换三份个人报告内容与截图。
- 不增加复杂生产架构；私有权限、离线队列等按后续需求再考虑。

# 原则

原生HTML/CSS/JS与标准库Python/SQLite，保持低资源与课程展示优先。SIM合成、TEST联调与REAL设备通道明确区分。电脑/模拟器测试不替代用户真机质量、GPS、FPS与长期稳定性反馈；人工复核为Demo交互，不是生产审批。部署仅独立路由/服务并先备份，不触碰myseoul其他应用。
