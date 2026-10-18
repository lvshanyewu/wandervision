---
schema_version: 1
type: "module"
id: "android"
title: "Android 本地视觉与构建"
summary: "3.3原生巡检App：COCO/RDD2022 Nano+Small、LIVE前台开关/HTTP1.1长流、内嵌手机地图，保留同帧定位照片/配对心跳/幂等上传；真机持续帧率与路害效果待验收。"
aliases: []
tags: ["Android", "NCNN"]
sources: ["android/app/build.gradle", "android/app/src/main/java/", "android/app/src/main/jni/*.cpp", "android/app/src/main/jni/*.h", "android/app/src/main/jni/CMakeLists.txt", "android/app/src/main/assets/*.ncnn.*", "android/app/src/main/res/", "android/app/src/main/AndroidManifest.xml", "tools/prepare_native.py", "tools/check_apk.py", "tools/check_report.py", "docs/inspection-record.md", "android/app/src/androidTest/", "docs/android-monitor.md"]
dependencies: []
related_modules: []
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-18T10:12:21+08:00"
---

# 当前实现

Android 3.3.0/versionCode6，包名io.github.lvshanyewu.wandervision。NCNN本地链路保留COCO Nano/Small，并新增RDD2022 Nano/Small四类路害（纵裂/横裂/龟裂/坑洞）；ModelCatalog稳定model_id与模型内class_id，车辆过滤只用于COCO。prepare_native调用prepare_models固定公开模型包/哈希，第三方来源许可随APK。输入320/480/640，CPU/系统Vulkan，无Turnip。

原生黑黄巡检UI，底部弹窗由下上移且提前定尺寸。LIVE需配对启用、首开披露公开演示用途，后续单击开关；JNI单槽带框最新帧长边640，JPEG55/80ms采样，TLS默认信任+主机名校验的HTTP1.1 chunked持续上传，UUID/公开状态匹配才显示接收FPS。55秒/14MiB轮换，2.5秒写watchdog，离开前台、切换镜头/模型、暂停/解除连接停止；无录像/后台服务。最后原生连续流帧率未验证，不可声称10–15FPS达成。

App内同源/mobile只读WebView地图/设备概览，无token URL、JS桥、网页位置/相机权限或宽松TLS，关闭销毁。保留简化本地仪表盘。

既有LocationTracker前台约10秒WGS84、30秒过期；JNI同帧取景生成含定位/检测信息JPEG与不可变schema1.0原JSON、EXIF与SHA；私有回执与手动幂等上传分离。MonitorLink独立网络队列，固定HTTPS配对/10秒前台心跳、凭据epoch fencing、隐私备份排除。模型/位置/相机实效仅由用户真机反馈确认。构建与边界详见docs/build-verification-3.3.0.md、docs/android-live.md。

# 构建与发布入口

Android 工程在 android/；Gradle9.4.1/AGP9.2.1/JDK21/SDK37.0/NDK29.0.14206865/CMake3.31.5。tools/prepare_native.py固定NCNN20260526与OpenCV4.13.0/v36官方包SHA256。常规assembleDebug默认仅ARM64；-PtestAbi=x86_64仅供模拟器，不得当作手机交付；tools/check_apk.py强制ARM64和Nano/Small两对模型。

GitHub Actions执行assembleDebug lintDebug、模型/ABI/签名检查，v*标签自动预发布。0.2.0起使用专用固定测试签名，密钥来自GitHub加密Secrets；本机密钥在仓库外，通过WV_KEYSTORE_PATH/WV_STORE_PASSWORD可选传入，别名wandervision；不得提交私钥。0.1.0升级签名冲突需卸载。

# 环境
Windows11；Android Studio D:/AndroidStudio；SDK D:/AndroidStudioSDK。ADB MCP 路径 D:/Androiddevedoc/adb-mcp/adb-mcp.exe，可通过 tools/adb_mcp.py 调用。不要将机器路径或签名私钥放入构建源码。
# 验证边界与后续

用户已确认0.1正常启动/夜间车辆识别尚可、0.2功能性无问题；不能推断量化FPS/GPU/发热/长时间稳定性全项通过。3.1本地ARM64构建/lint、签名/包检查通过；模拟器检查同帧照片、JSON/EXIF/校验和、定位更新/关闭/拒绝权限/近似授权/离开停止、横竖屏面板等。模拟器不是小米13相机方向、实际GNSS质量或骁龙性能证据。3.1真机验收与最终发布结果写continuation和docs/build-verification-3.1.0.md。3.2已通过实际HTTPS监控联动测试；独立Web工作仍由web-dashboard模块跟踪。病害模型/Turnip仍需独立转换和真机实验。

# 监控中心联动（3.2.0）

MonitorApi固定https://lvshanyewu.site/wandervision，只用系统TLS，不跟随API重定向。6位一次性码配对，私有monitor设置保存device/token/连接开关，epoch防止旧回调覆盖新会话。令牌不进URL/原图/JSON/日志；备份与设备转移排除私有偏好和文件。系统浏览器用自行构造的?device=PHONE-xx打开，忽略服务端任意dashboard_url，原地图入口已激活为仪表盘，本机统计保留。

MonitorLink独立网络线程；前台且启用/已配对每10秒心跳，onPause取消后续调度，无后台服务。未配对只做轻量前台状态检查，可恢复旋转期间完成的配对。空/过期/系统mock/已知模拟器不发送有效GPS，发送前复核年龄；未知速度不填0，GPS m/s转换km/h。已开始的请求可完成。进入浏览器会暂停App心跳，返回恢复。

照片逐张手动上传完整schema1.0对象+原JPEG Base64.NO_WRAP，4MiB图片/6MiB请求且先核SHA256；COCO类别保持原义。原JSON/JPEG不可变，files/uploads/UUID.json另存服务器ID/时间/duplicate/次数/错误。失败可用同UUID重试幂等，401/403只清除匹配旧连接；原图移除/编辑哈希不符时拒绝，不自动重压缩。已上传回执属于其他设备时要求切回原设备。公开课程Demo可查看照片/位置、可清库，提示紧邻按钮；旧local_only仅描述生成时状态，当前以回执为准。无后台自动上传或可靠自动队列。

18项隔离假传输Android Instrumentation通过，另有App真实界面到公网配对/心跳/拍照/上传/重复与浏览器验证：PHONE-01测试位，EF0报告只有一档案、duplicate=true，原JSON对象/JPEG字节相同，服务器test_data=true且无有效位置。证据详见docs/android-monitor.md、docs/build-verification-3.2.0.md与android-link-3.2进度。source=real只表示设备HTTP通道，不是实体手机或真实GNSS/识别验收；待小米13安装反馈。
