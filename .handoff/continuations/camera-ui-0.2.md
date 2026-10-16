---
schema_version: 1
type: "continuation"
id: "camera-ui-0.2"
title: "0.2.0 专业手持识别仪界面与双模型"
module_id: "android"
status: "done"
next_step: "用户安装0.2.0在小米13确认UI、左右横屏/前摄方向、Small对比、截图、GPU与持续稳定性。"
updated_at: "2026-10-16T22:35:18+08:00"
external_checks: [{"service": "GitHub Actions", "status": "passed", "evidence": "https://github.com/lvshanyewu/wandervision/actions/runs/38916332215 ; v0.2.0/bae25f3: assembleDebug lintDebug, ABI/models/signature and automatic Release all passed.", "checked_at": "2026-10-16T14:34:37Z"}, {"service": "Windows ADB MCP", "status": "passed", "evidence": "Final signed ARM64 assembleDebug + lintDebug passed via gradle_build; lint 0 errors/14 warnings; local APK content/signature/zipalign checked. x86_64 emulator UI paths checked separately.", "checked_at": "2026-10-16T14:34:37Z"}, {"service": "Release asset", "status": "verified", "evidence": "40561191 bytes; SHA256 d7a68b345f902102cfc846bb9f7f4295e7e7ac1ea3a51a2b412cba4148b9ac4e; downloaded Release matches checksum, two source model pairs, ARM64, version0.2.0/2, CAMERA only. Certificate SHA256 fcd138196a9ef353dbe2e98a23ee22da0c6ed0a6cef442599d59d742c56dbbac.", "checked_at": "2026-10-16T14:34:37Z"}]
---

# 交付完成
Release: https://github.com/lvshanyewu/wandervision/releases/tag/v0.2.0
APK: https://github.com/lvshanyewu/wandervision/releases/download/v0.2.0/wandervision-0.2.0-arm64.apk
本地同一产物artifacts/wandervision-0.2.0-arm64.apk，40561191字节；SHA256 d7a68b345f902102cfc846bb9f7f4295e7e7ac1ea3a51a2b412cba4148b9ac4e。源标签v0.2.0/bae25f3。
# 本轮功能
原生专业相机/手持仪器UI、金属参数键与立体快门、液晶HUD；Nano保留+Small；通用/车辆/纯取景、320/480/640、CPU/Vulkan；真实本地统计、相册截图/最近记录/查看/系统分享；网格与许可查看。小米13官方20:9/1080x2400适配，横屏右侧控制区。预览统一跟随显示方向，改变旧重力回转链路。GPS/云端明确未接入。
# 测试与修复
Windows ADB MCP最终签名ARM64编译/lint通过，lint0错误14警告；CI38916332215通过并自动Release。最终发布文件下载、SHA、签名、ARM64/版本/权限/模型与ZIP对齐验证通过。
1080x2400 x86_64模拟器启动、Nano/Small、横竖屏恢复、纯取景、仪表盘、MediaStore保存与详情已检查。回读保存图发现Window复制缺相机Surface的黑图问题，已修复独立Surface复制与HUD合成，最终JPEG回读确认有相机彩色图。模拟器前摄配置none，失败提示/切回后摄恢复已检查，未验证真前摄镜像。无观察到的AndroidRuntime崩溃。
# 签名与用户提醒
0.2.0开始固定专用测试签名，私钥仓库外/CI加密Secrets，未提交Git。0.1.0覆盖签名冲突需卸载旧包（清除数据）；以后同签名版本可覆盖。
# 未验证范围
没有小米13连接；模型效果、左右横屏/前摄方向、GPU/FPS/发热和长期稳定性仍待用户。测试说明docs/releases/0.2.0.md；证据docs/build-verification-0.2.0.md。
