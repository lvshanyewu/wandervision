---
schema_version: 1
type: "continuation"
id: "first-apk"
title: "P0 首个 ARM64 APK"
module_id: "android"
status: "open"
next_step: "跟进 GitHub Actions 编译诊断，完成本地 NDK 安装后通过 ADB MCP 构建；检查签名与包内容并发布 Release。"
updated_at: "2026-10-16T21:09:13+08:00"
---

# 已完成
Vervision 项目与路线/Android 模块；GitHub main 首次提交 a6ba2ba 已推送。固定上游 f1ac75e，保留原生摄像头和动态形状检测链路。单 COCO Nano 模型；通用物体/车辆类过滤；CPU/Vulkan；320/480/640。独立安装包名、权限回调和重复开启防护。来源许可、构建和真机测试文档。
# 构建与检查
ADB MCP stdio/tools/list/list_devices 已验证。NCNN20260526 和 OpenCV4.13.0/v36 下载及 SHA256 通过。GitHub Actions 38905617210 进行中。本地 JBR21/Gradle9.4.1/AGP9.2.1 配置已解析；当前 NDK 安装未完成导致 CXX1101，正通过已校验的官方清单链接下载 NDK；尚未产生 APK。
# 验证边界
无设备连接。真机相机、检测、FPS、稳定性待用户测试。
