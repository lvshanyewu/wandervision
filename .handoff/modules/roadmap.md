---
schema_version: 1
type: "module"
id: "roadmap"
title: "目标与分阶段路线"
summary: "校园Web v2已部署，Android3.2与Web配对/心跳/原图上传已在模拟器联调；下一步用户真机验收与病害模型评估。"
aliases: []
tags: ["规划", "MVP"]
sources: ["README.md", "AGENTS.md", "docs/web-dashboard.md", "docs/android-monitor.md"]
dependencies: []
related_modules: ["android", "web-dashboard"]
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-17T21:42:34+08:00"
---

# 目标
Android本地视觉、定位巡检记录；轻量云接收图片和JSON；Web地图、设备与统计。优先完整、稳定、低资源课程演示。

# 阶段与任务
- Android基础已实现NCNN Nano/Small ARM64、黑灰黄原生UI、同帧照片/报告、前台WGS84定位；真实设备效果以用户安装反馈为准。
- Web v1已完成重庆10模拟+3PHONE位独立服务并部署。
- 2026-10-17校园v2已部署：交大南岸中心、附近道路路线、五场景预设、详细透视合成画面、设置/存储清理/保留配额。
- Android3.2与Web联动已实现6位配对、前台心跳、照片详情手动上传原JPEG/JSON及幂等回执、系统浏览器查看地图。模拟器实际UI到公网联调通过；构建/发布记录由android-link-3.2与Web continuation跟踪。
- 下一步：用户小米13安装验收配对/拍照上传/地图，确认实际GNSS、摄像头方向、模型效果及稳定性。
- 病害阶段：RDD2022与NCNN兼容性/模型体积/真机性能、标注样本评估；现有COCO不声称识别路害。
- 后续可选：可靠离线同步队列、低频预览/实时视频、人工复核、私有访问控制、课设材料。

# 原则
原生HTML/CSS/JS与标准库Python/SQLite；不复杂工程化。SIM合成与TEST模拟器和真实上传来源明确区分。真实视频未接入；所有电脑/模拟器测试不代替用户真机质量、FPS和长期稳定性反馈。部署只独立路由/服务，备份后变更，不触碰myseoul其他应用。
