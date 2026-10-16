# wandervision

轻量级道路智能巡检课程设计。当前交付 **0.1.0 ARM64 Android 测试版**：完全本地运行的 YOLOv8 Nano + NCNN 摄像头检测。

- **通用物体**：COCO 80 类，可用杯子、瓶子、椅子、手机、键盘等室内物品验证。
- **车辆**：同一 COCO 模型，仅显示 bicycle、car、motorcycle、bus、truck；不是另行训练的专用车辆模型。
- 输入尺寸 320 / 480 / 640；CPU 默认，支持 Vulkan GPU；不含 Turnip 自定义驱动。
- Android 7.0+、ARM64、Camera2；目标测试手机为骁龙 8 Gen 2。

下载与反馈见 [GitHub Releases](https://github.com/lvshanyewu/wandervision/releases) 和 [测试说明](docs/releases/0.1.0.md)。构建通过不代表真机检测效果、性能和稳定性已经验证。

## 工程

`android/` 是可直接导入 Android Studio 的工程，`tools/` 是依赖准备、ADB MCP 桥接与 APK 检查脚本，`.handoff/` 是 Vervision 项目知识、阶段与进度记录。

首版保留上游 Java/JNI 命名空间 `com.tencent.yolov8ncnn` 以减少推理链路改动；安装包名独立为 `io.github.lvshanyewu.wandervision`，可与原版并存。界面先保留简化原生控件；完整 Material Design 3 巡检界面放在后续阶段。

## 构建

要求 JDK 21、Python 3.11+、Android SDK，安装 `platforms;android-37.0`、`build-tools;36.0.0`、`ndk;29.0.14206865`、`cmake;3.31.5`。Gradle Wrapper 9.4.1 / AGP 9.2.1。

```powershell
python tools/prepare_native.py
$env:JAVA_HOME='D:\AndroidStudio\jbr'
$env:ANDROID_HOME='D:\AndroidStudioSDK'
cd android
.\gradlew.bat assembleDebug lintDebug
cd ..
python tools/check_apk.py android/app/build/outputs/apk/debug/app-debug.apk
```

APK：`android/app/build/outputs/apk/debug/app-debug.apk`。首次依赖下载需要网络；原生依赖固定版本并核对 SHA-256，不进入 Git。Windows 可在 `android/local.properties` 配置 `sdk.dir=D:/AndroidStudioSDK`。

GitHub Actions 对 main 和 v* 标签执行构建、lint 和包内容检查，标签构建自动创建预发布版。该版本使用测试签名，请勿用于生产；本地与 CI 测试签名不同，切换来源安装可能需要卸载旧版（会清除应用数据）。

## 后续路线

真机反馈 → 道路病害模型兼容/移植 → MD3 巡检界面与 GPS/事件/截图 → 轻量 API 和数据库 → Web 地图与统计 → 指标、课设文档与演示。车牌和遗留物检测为可选扩展；首版不含病害模型、GPS、上传、后端或 Web。

## 来源与许可

基于 [nihui/ncnn-android-yolov8](https://github.com/nihui/ncnn-android-yolov8)，固定来源提交 `f1ac75ec54ccb3817a9eba620fe51da8bdcf87ca`。模型直接使用该提交中已经适配动态形状的 COCO YOLOv8n NCNN 文件，没有重新训练或重新导出。

本项目以 AGPL-3.0 发布；上游 BSD/Apache 等许可声明继续保留。详情见 [第三方说明](THIRD_PARTY_NOTICES.md) 和 `licenses/`。
