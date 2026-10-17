---
schema_version: 1
type: "continuation"
id: "device-validation-3.1"
title: "等待小米13反馈：3.1定位与巡检照片"
module_id: "android"
status: "open"
next_step: "安装3.1.0覆盖升级，反馈定位授权/室外首次定位、横竖屏照片与检测框、JSON导出、耗电和持续稳定性。"
updated_at: "2026-10-17T20:20:25+08:00"
---

# 待验收

应用版本3.1.0（code4），保留Nano/Small和CPU/系统Vulkan，新增前台定位、上滑面板、同帧取景报告与JSON导出；未集成病害模型、Turnip或监控中心上传。

# 检查重点

小米13验证fine/coarse/拒绝定位、室外GPS位置及精度/年龄、关闭和返回后的恢复；竖屏/左右横屏/前摄报告方向与非空检测框结果是否对应；照片+JSON分享和文件导出；覆盖后旧截图；持续运行耗电/发热。位置写入照片及EXIF，分享时会一起分享。真实设备结果待用户提供，不从模拟器推断。

# 开发机证据

本地ADB MCP构建/lint、签名/ABI/对齐和模拟器流程通过，详见docs/build-verification-3.1.0.md、docs/releases/3.1.0.md。旧0.3验收由此版本接续，不代表0.3收到真机全项通过反馈。
