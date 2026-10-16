---
schema_version: 1
type: "continuation"
id: "first-apk"
title: "P0 首个 ARM64 APK"
module_id: "android"
status: "done"
next_step: "由用户安装 v0.1.0 按 device-validation 清单反馈；根据反馈再启动病害模型或界面迭代。"
updated_at: "2026-10-16T21:19:04+08:00"
external_checks: [{"service": "GitHub Actions", "status": "passed", "evidence": "https://github.com/lvshanyewu/wandervision/actions/runs/38906201825 ; v0.1.0/f921fee: assembleDebug lintDebug, ARM64/model packaging and APK v2 signature passed; Release published.", "checked_at": "2026-10-16T13:18:28Z"}, {"service": "Windows ADB MCP", "status": "passed", "evidence": "gradle_build assembleDebug --offline: BUILD SUCCESSFUL 29s; lintDebug --offline --rerun-tasks: BUILD SUCCESSFUL, 0 errors/6 warnings. Logs artifacts/mcp-build.log and artifacts/mcp-lint.log. Local APK structural/signature checks passed.", "checked_at": "2026-10-16T13:18:28Z"}, {"service": "GitHub Release download", "status": "verified", "evidence": "https://github.com/lvshanyewu/wandervision/releases/tag/v0.1.0 ; artifacts/wandervision-0.1.0-arm64.apk 19698170 bytes; SHA256 fad588cf28f9e00bc03fdb95fe4f3ca58788e3c1d472077d1cfbe3ad70eeccc7 matches Release SHA256SUMS; apksigner v2 and zipalign16K passed.", "checked_at": "2026-10-16T13:18:28Z"}]
---

# 交付完成
Release: https://github.com/lvshanyewu/wandervision/releases/tag/v0.1.0
APK: https://github.com/lvshanyewu/wandervision/releases/download/v0.1.0/wandervision-0.1.0-arm64.apk
本地同一文件: artifacts/wandervision-0.1.0-arm64.apk（19698170 bytes）；SHA256 fad588cf28f9e00bc03fdb95fe4f3ca58788e3c1d472077d1cfbe3ad70eeccc7。源标签 v0.1.0 / f921fee。
# 已完成任务
项目初始化、GitHub 代码推送、Vervision 目标路线任务记录；复用已克隆上游并固定来源提交；单 Nano 模型、通用/车辆模式、320/480/640、CPU/Vulkan、默认后摄；权限与相机主要错误处理；许可与基础文档；CI 与 Windows ADB MCP 构建/lint；APK 静态冒烟、签名、对齐检查；GitHub 预发布和本地保存。
# 证据与检查
详见 docs/build-verification-0.1.0.md。CI标签构建38906201825通过。Windows SDK/NDK29/CMake3.31.5已可用，MCP assembleDebug29s、lint0错误6警告。最终Release下载与校验和一致，仅CAMERA权限、ARM64、model资产及许可正确。Vervision格式与来源校验通过。
# 未验证范围
没有ARM64手机连接，没有声称真机安装、相机、检测质量、FPS、GPU性能、发热或长期稳定性通过。用户真机验收转入device-validation任务。未开发病害/GPS/后端/Web。
