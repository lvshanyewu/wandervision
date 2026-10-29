---
schema_version: 1
type: "module"
id: "android"
title: "Android 本地视觉与构建"
summary: "3.4.1 Android课设采集：完整schema1.0报告+原JPEG上传，采集点/备注在拍照时冻结；沿用十模型、OCR、固定机位规则与配对/LIVE。"
aliases: []
tags: ["Android", "NCNN"]
sources: ["android/app/build.gradle", "android/app/src/main/java/", "android/app/src/main/jni/*.cpp", "android/app/src/main/jni/*.h", "android/app/src/main/jni/CMakeLists.txt", "android/app/src/main/assets/*.ncnn.*", "android/app/src/main/res/", "android/app/src/main/AndroidManifest.xml", "tools/prepare_native.py", "tools/check_apk.py", "tools/check_report.py", "docs/inspection-record.md", "android/app/src/androidTest/", "docs/android-monitor.md"]
dependencies: []
related_modules: []
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-29T15:26:02+08:00"
---

# 当前实现

Android 3.4.1/versionCode8，包名io.github.lvshanyewu.wandervision。NCNN本地链路保留COCO Nano/Small、RDD2022 Nano/Small；新增BDD100K Nano、交通标志36类/21类、车牌YOLOv8n、垃圾6类、TACO YOLO11s九类，合计十检测器，一次只加载所选模型。ModelCatalog使用稳定model_id与模型内class_id；COCO/BDD分别按自身类别过滤车辆。输入320/480/640，CPU/系统Vulkan，无Turnip。prepare_native调用prepare_models与prepare_experiments下载固定公开模型包，所有新增产物和字典校验SHA256；来源许可随APK。

车牌模式按快门从同一原生帧裁剪并用HyperLPR3识别器NCNN CPU推理，候选/置信度写照片与JSON plate_recognition。不含四角矫正/双层合并。官方77字典与78输出差异以拒绝未知索引处理。模型与边界详见docs/teacher-scope-feasibility.md、ocr-model-manifest.json。

实验室提供自有轻量类别一致IoU/中心点关联的固定机位ROI停留、逆向、虚拟线跨越和限定已知类别道路异物提示。用户须手选区域/方向并确认固定手机；提示不等于交通违法、实线/应急车道自动识别、真实物理量。规则不持久启用，切镜头/模型、后台/相机关闭自动关闭。JSON experimental_analysis随捕获原生帧导出。可选1280×720采集，创建失败/无首帧回退640×480；一键恢复COCO Nano/CPU/320/基线采集并关闭规则。

原生黑黄巡检UI，底部弹窗由下上移。LIVE需配对启用、首开披露公开演示用途，后续单击开关；JNI单槽带框最新帧，HTTP1.1 chunked持续上传。离开前台、切换镜头/模型、暂停/解除连接停止；无录像/后台服务。真实小米13连续帧率不可由模拟器推断。

App内同源/mobile只读WebView地图/设备概览，无token URL、JS桥、网页位置/相机权限或宽松TLS，关闭销毁。保留简化本地仪表盘。LocationTracker前台约10秒WGS84、30秒过期；同帧取景生成定位/检测JPEG、不可变schema1.0 JSON、EXIF/SHA；回执与手动幂等上传分离。新字段作为可选扩展，未修改Web/API。

3.4.1副标题“道路巡检具身智能体”，设置中采集点与备注仅影响后续新照片，快门时冻结顶层capture_context.site_name/note（80/240 Unicode码点，空字符串可用），原JSON完整保存，照片两行缩略、详情完整显示。原schema1.0报告已含逐目标label/class_id/confidence/bbox、OCR候选和实验规则，仍整份report+原JPEG上传，保留event_id幂等与不可变原记录，无服务端协议变更。详见docs/android-data-center-3.4.1.md。

# 构建与发布入口

Android工程在android/；Gradle9.4.1/AGP9.2.1/JDK21/SDK37.0/NDK29.0.14206865/CMake3.31.5。tools/prepare_native.py固定NCNN20260526与OpenCV4.13.0/v36官方包SHA256，并调用prepare_models.py和prepare_experiments.py安装固定Release模型包。常规assembleDebug默认仅ARM64；-PtestAbi=x86_64仅供模拟器，不得当作手机交付。tools/check_apk.py检查仅ARM64、十检测器/OCR/字典和新增资产哈希。源权重/转换工具与清单见tools/export_experiment_models.py、docs/experiment-models-manifest.json、docs/ocr-model-manifest.json，权重不得提交Git。

GitHub Actions执行assembleDebug lintDebug、模型/ABI/签名检查，v*标签自动预发布，已有Release时保留已发布资产。0.2.0起使用专用固定测试签名，密钥来自GitHub加密Secrets；本机密钥在仓库外，通过WV_KEYSTORE_PATH/WV_STORE_PASSWORD传入，别名wandervision；不得提交私钥。0.1.0升级签名冲突需卸载。

# 环境
Windows11；Android Studio D:/AndroidStudio；SDK D:/AndroidStudioSDK。ADB MCP 路径 D:/Androiddevedoc/adb-mcp/adb-mcp.exe，可通过 tools/adb_mcp.py 调用。不要将机器路径或签名私钥放入构建源码。
# 验证边界与后续

用户已确认早期版本正常启动、夜间车辆识别尚可及功能正常；不能推断新模型实景准确率、GPU、发热或长时间稳定性。3.4最终x86_64模拟器55项契约/原生模型检查通过；六检测器18个矩形/方形输入转换数值对照、OCR ONNX/NCNN数值对照通过，同帧车牌模式无目标照片/JSON完整性已检查。ARM64 assembleDebug lintDebug、check_apk、apksigner与16KiB zipalign通过，发布结果见continuation及docs/build-verification-3.4.0.md。

真实小米13相机、720p支持、GNSS、各模型精度/FPS和车牌可读率由用户测试。积水权重未找到明确再分发许可，未打包；设施损坏、事故、专用车型、真实超速/超高/车距未实现。固定机位规则不是移动相机行为识别。研究逐项映射见docs/teacher-scope-feasibility.md。

# 监控中心联动（3.2.0）

MonitorApi固定https://lvshanyewu.site/wandervision，只用系统TLS，不跟随API重定向。6位一次性码配对，私有monitor设置保存device/token/连接开关，epoch防止旧回调覆盖新会话。令牌不进URL/原图/JSON/日志；备份与设备转移排除私有偏好和文件。系统浏览器用自行构造的?device=PHONE-xx打开，忽略服务端任意dashboard_url，原地图入口已激活为仪表盘，本机统计保留。

MonitorLink独立网络线程；前台且启用/已配对每10秒心跳，onPause取消后续调度，无后台服务。未配对只做轻量前台状态检查，可恢复旋转期间完成的配对。空/过期/系统mock/已知模拟器不发送有效GPS，发送前复核年龄；未知速度不填0，GPS m/s转换km/h。已开始的请求可完成。进入浏览器会暂停App心跳，返回恢复。

照片逐张手动上传完整schema1.0对象+原JPEG Base64.NO_WRAP，4MiB图片/6MiB请求且先核SHA256；COCO类别保持原义。原JSON/JPEG不可变，files/uploads/UUID.json另存服务器ID/时间/duplicate/次数/错误。失败可用同UUID重试幂等，401/403只清除匹配旧连接；原图移除/编辑哈希不符时拒绝，不自动重压缩。已上传回执属于其他设备时要求切回原设备。公开课程Demo可查看照片/位置、可清库，提示紧邻按钮；旧local_only仅描述生成时状态，当前以回执为准。无后台自动上传或可靠自动队列。

18项隔离假传输Android Instrumentation通过，另有App真实界面到公网配对/心跳/拍照/上传/重复与浏览器验证：PHONE-01测试位，EF0报告只有一档案、duplicate=true，原JSON对象/JPEG字节相同，服务器test_data=true且无有效位置。证据详见docs/android-monitor.md、docs/build-verification-3.2.0.md与android-link-3.2进度。source=real只表示设备HTTP通道，不是实体手机或真实GNSS/识别验收；待小米13安装反馈。
