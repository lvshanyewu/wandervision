# wandervision

轻量级道路智能巡检课程设计。当前交付 **0.3.0 FIELD UI**：黑灰黄原生巡检界面，完全本地运行的 YOLOv8 Nano / Small + NCNN 摄像头检测。

- **通用物体**：COCO 80 类，可用杯子、瓶子、椅子、手机、键盘等室内物品验证。
- **车辆**：同一 COCO 模型，仅显示 bicycle、car、motorcycle、bus、truck；不是另行训练的专用车辆模型。
- 输入尺寸 320 / 480 / 640；CPU 默认，支持 Vulkan GPU；不含 Turnip 自定义驱动。
- **纯取景**：不执行推理，便于检查相机、方向和界面；新增真实检测统计、构图网格、相册截图、最近记录与系统分享。
- Android 7.0+、ARM64、Camera2；截图保存需 Android10+。目标设备小米13，6.36英寸2400×1080/20:9；dp与系统安全区域适配，横屏控制区放在右侧。

下载与反馈见 [GitHub Releases](https://github.com/lvshanyewu/wandervision/releases) 和 [0.3.0测试说明](docs/releases/0.3.0.md)。用户已确认0.2.0功能无问题；0.3.0仅重排和重绘界面，不新增模型或业务能力。

## 工程

`android/` 是可直接导入 Android Studio 的工程，`tools/` 是依赖准备、ADB MCP 桥接与 APK 检查脚本，`.handoff/` 是 Vervision 项目知识、阶段与进度记录。

保留上游 Java/JNI 命名空间 `com.tencent.yolov8ncnn`；安装包名独立为 `io.github.lvshanyewu.wandervision`，可与原版并存。界面采用原生View、自绘仪器外观与渐变控件，无WebView；实时状态来自JNI。推理输出布局与模型预处理沿用上游，预览按实际显示方向旋转并等比裁切。

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

APK：`android/app/build/outputs/apk/debug/app-debug.apk`。首次依赖下载需要网络；原生依赖固定版本并核对 SHA-256，不进入 Git。Windows 可在 `android/local.properties` 配置 `sdk.dir=D\:/AndroidStudioSDK`（属性文件中盘符冒号需要转义）。

已安装 ADB MCP 时，可通过可选桥接脚本调用它（工具不在 PATH 时传 `--server`，SDK 不在环境中时传 `--sdk`）：

```powershell
python tools/adb_mcp.py --server D:/Androiddevedoc/adb-mcp/adb-mcp.exe --sdk D:/AndroidStudioSDK list_devices
python tools/adb_mcp.py --server D:/Androiddevedoc/adb-mcp/adb-mcp.exe --sdk D:/AndroidStudioSDK gradle_build '{"project_dir":"D:/wandervision/android","task":"assembleDebug","args":["--console=plain"]}'
```

GitHub Actions 对 main 和 v* 标签执行构建、lint 和包内容/签名检查，标签构建自动预发布。0.2.0起CI使用专用固定测试签名，私钥由仓库Secrets提供，不提交Git；本地可选用 `WV_KEYSTORE_PATH` / `WV_STORE_PASSWORD`（别名wandervision）使用相同身份，否则为本机默认debug签名。0.1.0升级若签名冲突需先卸载，会清除应用数据。

模拟器界面测试可使用 `assembleDebug -PtestAbi=x86_64`，此选项仅供开发验证；交付默认ARM64，`tools/check_apk.py`会拒绝x86包。不要把模拟器FPS当作小米13性能。

## 后续路线

当前已完成黑灰黄巡检UI、截图和本地统计，地图/轨迹仅为禁用占位。后续：真机反馈 → 道路病害模型 → GPS与本地事件库 → 轻量API/数据库/事件同步 → Web地图统计 → 课设材料。车牌和遗留物检测可选；GPS、云同步、后端和Web尚未接入，界面不显示虚构的位置或同步成功状态。

## 来源与许可

基于 [nihui/ncnn-android-yolov8](https://github.com/nihui/ncnn-android-yolov8)，固定来源提交 `f1ac75ec54ccb3817a9eba620fe51da8bdcf87ca`。Nano和Small均直接使用该提交已适配动态形状的COCO NCNN文件，没有重新训练或重新导出。

本项目以 AGPL-3.0 发布；上游 BSD/Apache 等许可声明继续保留。详情见 [第三方说明](THIRD_PARTY_NOTICES.md) 和 `licenses/`。
