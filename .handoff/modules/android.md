---
schema_version: 1
type: "module"
id: "android"
title: "Android 本地视觉与构建"
summary: "本地NCNN Nano/Small检测，专业手持仪器相机UI、原生实时统计与截图记录，适配小米13横竖屏。"
aliases: []
tags: ["Android", "NCNN"]
sources: ["android/app/build.gradle", "android/app/src/main/java/", "android/app/src/main/jni/*.cpp", "android/app/src/main/jni/*.h", "android/app/src/main/jni/CMakeLists.txt", "android/app/src/main/assets/*.ncnn.*", "android/app/src/main/res/", "android/app/src/main/AndroidManifest.xml", "tools/prepare_native.py", "tools/check_apk.py"]
dependencies: []
related_modules: []
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-16T22:31:57+08:00"
---

# 当前实现

基于 nihui/ncnn-android-yolov8 提交 f1ac75ec54ccb3817a9eba620fe51da8bdcf87ca，保留 NCNN 动态形状 COCO 推理预处理/输出解析。Nano 与 Small 两对原生模型，不训练/重导出；通用80类、车辆COCO1/2/3/5/7过滤、纯取景关闭推理；320/480/640、CPU/Vulkan，实际GPU不可用时回退CPU。

CameraUi 使用原生 View 与自绘暗色仪器外观：安全区、金属渐变参数键、液晶状态HUD、立体快门、横屏右侧控制区。JNI输出真实显示帧率、推理耗时、当前帧目标/最高置信度/帧数/实际引擎。统计面板为打开时快照和近期采样FPS，不是去重车辆数。

快门先 PixelCopy Window，再独立 PixelCopy 相机 Surface，将相机画面与HUD合成；不能假设Window复制会包含SurfaceView，已在模拟器保存图回读中发现并修复黑图问题。Android10+通过MediaStore保存Pictures/wandervision；SharedPreferences记录最近30张的Uri与状态；查看与系统分享。没有录像、网络、GPS或云同步；未接入能力明确标注。设置提供网格、应用权限入口和随包许可证查看。

预览不再依靠独立重力旋转并回转。Java传递实际DisplayRotation/Surface尺寸，native按相机sensorOrientation与前后镜头处理方向，等比中心裁切，再检测/绘制/渲染；Window交换有互斥保护。此方向改动仍需小米13实机验证。

# 构建与发布入口

Android 工程在 android/；Gradle9.4.1/AGP9.2.1/JDK21/SDK37.0/NDK29.0.14206865/CMake3.31.5。tools/prepare_native.py固定NCNN20260526与OpenCV4.13.0/v36官方包SHA256。常规assembleDebug默认仅ARM64；-PtestAbi=x86_64仅供模拟器，不得当作手机交付；tools/check_apk.py强制ARM64和Nano/Small两对模型。

GitHub Actions执行assembleDebug lintDebug、模型/ABI/签名检查，v*标签自动预发布。0.2.0起使用专用固定测试签名，密钥来自GitHub加密Secrets；本机密钥在仓库外，通过WV_KEYSTORE_PATH/WV_STORE_PASSWORD可选传入，别名wandervision；不得提交私钥。0.1.0升级签名冲突需卸载。

# 环境
Windows11；Android Studio D:/AndroidStudio；SDK D:/AndroidStudioSDK。ADB MCP 路径 D:/Androiddevedoc/adb-mcp/adb-mcp.exe，可通过 tools/adb_mcp.py 调用。不要将机器路径或签名私钥放入构建源码。
# 验证边界与后续

用户已反馈0.1.0正常启动且黑夜车辆识别尚可；不能推断所有FPS/GPU/长时间稳定性已通过。0.2.0在1080x2400 x86_64模拟器检查UI、模型切换、横竖屏、纯取景、仪表盘与截图持久化；模拟器不是小米13性能证据。真机检测、方向、GPU与发热仍待用户验收。后续病害模型、GPS事件、服务端同步/Web保持规划状态。发布与量化检查写continuation和docs/releases。
