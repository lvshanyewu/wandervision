---
schema_version: 1
type: "continuation"
id: "device-validation"
title: "等待用户反馈：0.1.0 ARM64 真机验收"
module_id: "android"
status: "open"
next_step: "用户在骁龙8Gen2安装Release APK，反馈摄像头/检测/FPS/稳定性；收到反馈后优先修复问题，再推进下一阶段。"
updated_at: "2026-10-16T21:19:04+08:00"
---

# 用户测试入口
https://github.com/lvshanyewu/wandervision/releases/tag/v0.1.0
详见 docs/releases/0.1.0.md。
# 测试任务
- 通用物体/320/CPU：室内杯子、瓶子、椅子、键盘等，观察预览、框、英文标签和FPS。
- 权限拒绝/允许；切换镜头；前后台恢复；旋转。
- 480/640、GPU切换，反馈FPS、发热与画面。
- 车辆模式仅显示COCO自行车/汽车/摩托/公交/卡车。
- 连续5–10分钟稳定性。
# 反馈字段
手机型号、Android版本、模式、输入尺寸、CPU/GPU、FPS、故障步骤和截图。
# 开发约束
等待真实反馈前不把真机结果写为通过；后续病害模型、MD3、GPS事件、后端Web保持规划状态。
