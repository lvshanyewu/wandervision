# 3.1.0 验证与交付记录

日期：2026-10-17（Asia/Shanghai）。应用源标签 `v3.1.0`，提交 `79ad8d30cea7f0e63e1198e59b52ea3eadf35fa6`。本轮只提交Android功能与对应文档；其他Web任务工作目录改动未纳入。

## 构建与包检查

- Windows通过ADB MCP完成最终ARM64 `assembleDebug lintDebug --offline`，Lint 0错误、21条非阻塞警告。包括既有API/ABI/自绘/RTL等警告及平台ExifInterface建议；本轮为避免新增依赖使用平台EXIF，在模拟器实际写入回读GPS标签验证，未屏蔽错误。
- 本地签名APK：40,610,746 bytes；SHA256 `6ae6937bd266acf93cffe6e381188f1f8e1959dd1d4229c61e84fc768c734382`，保留在忽略的artifacts目录。
- `tools/check_apk.py`、`apksigner verify --print-certs`、`zipalign -c -P 16 -v 4`通过。aapt确认版本3.1.0/code4、包名 `io.github.lvshanyewu.wandervision`、仅ARM64。
- 仅请求CAMERA、ACCESS_COARSE_LOCATION、ACCESS_FINE_LOCATION；没有INTERNET、后台位置或全盘相册权限。定位使用系统provider，不需要应用网络权限。
- 证书SHA256 `fcd138196a9ef353dbe2e98a23ee22da0c6ed0a6cef442599d59d742c56dbbac`，与0.2/0.3一致，可覆盖安装；密钥不在仓库。
- `git diff v0.3.0 v3.1.0 -- android/app/src/main/assets`为空，模型未变化。推理解码/阈值/相机旋转逻辑未变，JNI仅新增按需同帧抓取。

## 模拟器实际检查

Android34、1080×2400、420dpi，使用独立x86_64开发构建。交付仍是ARM64。

- 竖屏/横屏布局、底部面板和报告图视觉核验；dumpsys确认横屏面板为BOTTOM及自定义windowAnimations。应用面板统一动画，系统权限/分享/文件保存页不受应用动画控制。
- 保存纯取景、启用识别的照片和JSON；检测对象为空时保留空数组，纯取景的推理耗时/最高置信度为null。未拿模拟器空场景宣称非空对象的检测准确度。
- 平台GPS模拟坐标回调约10秒，dumpsys记录请求与回调；打开系统分享/文件页后应用注册移除。观察缓存过期显示“已过期”。
- fresh坐标/精度/样本年龄写入报告，Pillow回读JPEG GPS EXIF。定位关闭off、系统界面拒绝权限permission_denied仍可保存照片，JSON坐标为null，JPEG不写GPS。
- 通过系统弹窗授予近似权限，核对coarse=true/fine=false；此模拟器没有可用network位置时显示等待，修正误报“系统定位关闭”。没有把这一检查说成真实近似位置已获取。
- 横屏报告保存640×687，画面矩形[0,51,640,409]；坐标框以源帧为基准，报告中不含快门/侧栏/系统栏。新JSON含安装来源UUID。
- `tools/check_report.py`对实际导出的纯取景、检测、定位关闭、拒绝及横屏记录检查通过；核对时间/对象/位置状态/图像SHA256和尺寸。
- 系统分享页识别2文件；ACTION_CREATE_DOCUMENT保存JSON至Downloads，pull回内容与私有JSON完全相同。没有向任何外部接收人发送测试数据。
- 在已安装0.3的模拟器覆盖安装开发包，旧截图仍能显示；私有JSON新记录与旧截图共同显示。crash buffer未观察到崩溃。

本地证据：`artifacts/report-3.1-*.json/jpg`、`ui-3.1-*.png/xml`、`report-3.1-exported.json`，不提交个人测试数据或构建产物。模拟相机彩色块与定位坐标均是模拟输入；系统mock标记为false也不能当作真实GNSS证明。

## 研究与验收边界

病害模型与Turnip调查已完成，见[调查记录](road-models-and-turnip.md)。研究权重/驱动未进入APK，不宣称转换或加载成功。3.1.0保留现有COCO Nano/Small与CPU/系统Vulkan，不连接监控中心。

小米13真实定位精度/首次定位、近似授权行为、左右横屏/前摄照片方向、带非空识别框的同帧结果、分享接收端兼容、持续耗电/发热等仍待用户安装反馈；0.2的已知功能反馈不能替代本轮真机验收。测试重点见[发布说明](releases/3.1.0.md)，结构化字段与存储边界见[记录契约](inspection-record.md)。

## 最终发布包

- [标签CI](https://github.com/lvshanyewu/wandervision/actions/runs/39032008856)成功，自动发布[3.1.0 Release](https://github.com/lvshanyewu/wandervision/releases/tag/v3.1.0)。
- [最终APK](https://github.com/lvshanyewu/wandervision/releases/download/v3.1.0/wandervision-3.1.0-arm64.apk)：40,610,530 bytes，SHA256 `dec3f42465ef3705af2e5bf9a3a601568764eaac9a4aba9065bbfe8d35651d28`。
- 已从公开Release下载至 `artifacts/wandervision-3.1.0-arm64.apk`，核对发布SHA256SUMS一致；再次通过check_apk、apksigner、zipalign和aapt检查。证书与上述固定测试身份一致，可覆盖0.2/0.3。
- 回验时刻2026-10-17 20:21（Asia/Shanghai）。发布APK来源是标签CI，本地构建hash不同，交付以本节最终文件为准。
