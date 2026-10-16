---
schema_version: 1
type: "continuation"
id: "device-validation-0.3"
title: "等待小米13反馈：0.3.0黑灰黄界面"
module_id: "android"
status: "open"
next_step: "用户覆盖安装0.3.0后反馈字体、横竖屏布局、安全区、侧栏/模式交互、标注遮挡和截图记录。"
updated_at: "2026-10-16T23:25:18+08:00"
---

# 验收基线

用户已确认0.2.0功能性没有问题。0.3.0只重绘原生界面，未增加模型；本地与CI构建及模拟器UI冒烟通过，详见docs/build-verification-0.3.0.md。

# 待真机反馈

小米13覆盖升级，检查竖屏/左右横屏安全区、字体/黄色标注可读性、侧栏遮挡、快门/模型/CPU-GPU/模式切换、截图相册与历史记录。地图和轨迹灰色禁用属预期。不能由模拟器结果推断手机检测、FPS、发热和长期稳定性。

下载：https://github.com/lvshanyewu/wandervision/releases/tag/v0.3.0
