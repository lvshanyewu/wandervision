---
schema_version: 1
type: "overview"
id: "wandervision"
title: "wandervision 道路智能巡检"
summary: "道路巡检具身智能体课设：Web数据中心v4与Android3.4.1已交付，统一台账、人工复核、筛选导出和可视化报告；用户真机及课程材料验收待进行。"
aliases: []
tags: []
updated_at: "2026-10-17T21:45:00+08:00"
---

# 项目目标

wandervision：复用成熟 NCNN Android YOLOv8 链路，完成可运行、可演示的道路智能巡检系统。Android本地视觉与定位报告已实现；2026-10-29完成课程数据中心v4，前台展示与后台数据整理分开，Android3.4.1补充采集点与备注。

# 导航

- [数据中心v4交付与部署](continuations/data-center-v4.md)
- [台账与上报接口](../docs/data-center-contract.md)
- [课程报告更新交接](../docs/course-report-v4-update.md)

- [阶段路线与任务](modules/roadmap.md)
- [Android 本地推理与构建](modules/android.md)
- [Web 监控中心架构与接口](modules/web-dashboard.md)
- [校园 Web v2 开发/联动/部署结果](continuations/web-dashboard-v2.md)
- [Android3.2联动进度](continuations/android-link-3.2.md)
- [Web 首版历史](continuations/web-dashboard-v1.md)
- [Web 启动、部署与回退](../docs/web-dashboard.md)
- [首个 APK 当前进度](continuations/first-apk.md)
- [构建与使用](../README.md)
- [真机测试说明](../docs/releases/0.1.0.md)

# 工作边界

Web采用SIM合成设备及循环场景，TEST标注模拟器联调报告。Android3.2与Web配对、无GPS心跳、原图/JSON上传及幂等联调已通过；这些是历史联调证据；当前真实手机GNSS/相机/识别和LIVE效果仍待用户验收。保留第三方来源和许可。电脑端检查不能证明真机相机、推理质量、性能和稳定性已通过；阶段进展和发布结果持续同步 Vervision。
