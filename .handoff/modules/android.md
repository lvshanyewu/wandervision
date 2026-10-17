---
schema_version: 1
type: "module"
id: "android"
title: "Android 本地视觉与构建"
summary: "3.2原生巡检App：NCNN本地识别、同帧定位照片/JSON、HTTPS配对与前台心跳、手动幂等上传及系统浏览器仪表盘；未加入病害模型/Turnip。"
aliases: []
tags: ["Android", "NCNN"]
sources: ["android/app/build.gradle", "android/app/src/main/java/", "android/app/src/main/jni/*.cpp", "android/app/src/main/jni/*.h", "android/app/src/main/jni/CMakeLists.txt", "android/app/src/main/assets/*.ncnn.*", "android/app/src/main/res/", "android/app/src/main/AndroidManifest.xml", "tools/prepare_native.py", "tools/check_apk.py", "tools/check_report.py", "docs/inspection-record.md", "android/app/src/androidTest/", "docs/android-monitor.md"]
dependencies: []
related_modules: []
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-17T21:42:08+08:00"
---

# 当前实现

基于 nihui/ncnn-android-yolov8 提交 f1ac75ec54ccb3817a9eba620fe51da8bdcf87ca，保留NCNN动态形状COCO推理预处理/输出解析。Nano与Small原生模型，不训练/重导出；通用80类、车辆COCO1/2/3/5/7过滤、纯取景关闭推理；320/480/640、CPU/系统Vulkan，GPU不可用回退CPU。未添加病害模型或Turnip运行选项，调查见model-research模块。

CameraUi为原生View/Canvas黑灰黄界面，侧栏、快门、弧表/真实指标、横竖屏布局。3.1所有应用面板从底部上滑进入、下滑退出；系统权限/分享/文件选择器保留系统行为。统计面板是快照/显示FPS趋势，不是去重车辆数。地图/轨迹仍禁用占位。

3.1快门不再截图Window/UI：请求下一native帧，推理锁内按需复制已画框RGB、目标数组/统计与CLOCK_BOOTTIME时间，JNI返回FrameCapture。照片来源是按实际显示方向旋转并等比中心裁切的预览帧，不是高像素静态拍照。2秒超时失败不取旧图；保存时阻止切模型/输入/模式/镜头。InspectionReport生成带时间、WGS84位置/精度/年龄、模型/输入/引擎与记录号的JPEG，检测结果与照片同帧。旧版本PixelCopy黑图合成方案已被此路径替代，旧截图仍可查看/分享。

LocationTracker使用平台GPS/network，前台约10秒/0距离请求，无Play Services或后台服务。支持fine/coarse，拒绝或关闭定位仍可取景；最多接受2分钟缓存，超过30秒标stale，UI不伪造经纬度。coarse且无网络位置时等待，不误报系统定位关闭。JSON标WGS84、精度、样本年龄/时刻/权限/系统mock标记；模拟器geo fix可能不带mock标记。离开应用removeUpdates。新鲜/近似坐标写GPS EXIF，stale不写；分享照片包含位置。

Android10+通过MediaStore.pending保存Pictures/wandervision，回读JPEG算SHA256，AtomicFile持久化files/reports/UUID.json，随后发布图片并提交SharedPreferences最近30条索引。JSON带schema1.0、随机安装ID来源、帧/全部检测、位置质量、照片校验和与local_only；源content URI不能当服务端URL。image.viewfinder_rect_px映射归一化框到报告照片。最近30条不是JSON保留上限；卸载/清数据移除私有JSON。失败清理本次记录，崩溃恢复/配额/可靠上传队列尚未实现。ReportProvider仅通过临时授权只读分享UUID.json；支持照片+JSON分享和ACTION_CREATE_DOCUMENT导出JSON。3.2新增INTERNET权限用于用户启用的固定HTTPS监控连接，无录像。

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
