---
schema_version: 1
type: "continuation"
id: "device-validation-0.2"
title: "等待小米13反馈：0.2.0仪器界面与Small模型"
module_id: "android"
status: "open"
next_step: "用户安装Release，先Nano/320/CPU再Small/480/CPU，反馈UI、方向、截图与性能；优先修复再进入病害模型/GPS阶段。"
updated_at: "2026-10-16T22:35:18+08:00"
---

# 下载
https://github.com/lvshanyewu/wandervision/releases/tag/v0.2.0
0.1.0签名冲突需卸载后安装，新版固定测试签名。
# 真机重点
- 小米13竖屏、左右横屏、前摄镜像、前后台恢复。
- Nano基线与Small/480、640；夜间车辆/室内通用模型效果；CPU/Vulkan、实际FPS/发热。
- 纯取景、快门保存相册（包含画面和框）、详情与系统分享、网格与仪表盘。
- 连续5–10分钟稳定性。
# 反馈边界
用户已确认0.1.0启动/夜间车辆可用，不替代0.2.0验收；模拟器性能不是手机性能。具体测试见docs/releases/0.2.0.md。
