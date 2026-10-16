---
schema_version: 1
type: "module"
id: "android"
title: "Android 本地视觉与构建"
summary: "首版复用上游 NCNN YOLOv8，ARM64 通用物体及车辆测试。"
aliases: []
tags: ["Android", "NCNN"]
sources: ["android/app/build.gradle", "android/app/src/main/java/", "android/app/src/main/jni/*.cpp", "android/app/src/main/jni/*.h", "android/app/src/main/jni/CMakeLists.txt", "android/app/src/main/assets/*.ncnn.*", "android/app/src/main/res/", "android/app/src/main/AndroidManifest.xml", "tools/prepare_native.py", "tools/check_apk.py"]
dependencies: []
related_modules: []
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-16T21:12:46+08:00"
---

# 当前实现
首版基于 nihui/ncnn-android-yolov8 提交 f1ac75ec54ccb3817a9eba620fe51da8bdcf87ca；保留动态形状 NCNN COCO 检测和 NDK Camera2 链路。只打包 YOLOv8n 一对模型文件；通用模式与车辆类别过滤（COCO 1/2/3/5/7）。输入 320/480/640，默认 CPU，Vulkan GPU 可选，不包含 Turnip。独立安装包 io.github.lvshanyewu.wandervision，保留 Java/JNI 上游命名空间。应用启动默认后摄，处理首次授权回调、重复开启和生命周期关闭；模型/相机主要错误向 Java 返回。
# 构建与发布入口
Android 工程位于 android/。Gradle9.4.1、AGP9.2.1、JDK21、SDK37.0、NDK29.0.14206865、CMake3.31.5。依赖脚本固定 NCNN20260526 和 OpenCV4.13.0/v36 的官方发布包及 SHA256。README 包含本地构建命令；GitHub Actions 执行 assembleDebug lintDebug、APK 模型/ABI/ELF 与签名检查；v* 标签自动预发布。
# 环境
Windows11；Android Studio D:/AndroidStudio；SDK D:/AndroidStudioSDK。ADB MCP 路径 D:/Androiddevedoc/adb-mcp/adb-mcp.exe，可通过 tools/adb_mcp.py 调用。不要将机器路径或签名私钥放入构建源码。
# 验证边界与后续
没有 ARM64 设备连接；真实相机、检测质量、FPS、发热和稳定性需用户安装 APK 后验收。UI 仍为简化原生控件；完整 Material Design3、病害模型、GPS/事件记录及后端/Web 均为后续阶段。具体构建和发布结果见 first-apk continuation。
