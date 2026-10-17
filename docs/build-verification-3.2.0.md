# 3.2.0 构建与联动交付验证

日期：2026-10-17（Asia/Shanghai）。应用源标签 `v3.2.0`，提交 `31cb2304ca035e795183beb35e56164f539f63c8`。本轮仅Android及其交接/文档/发布流程；Web源、部署、README/AGENTS及Web交接改动未纳入。

## 构建与签名

- Windows通过ADB MCP完成最终ARM64 `assembleDebug lintDebug --offline`：0错误、22条非阻塞警告，无屏蔽错误。主流程继续复用NCNN；native源码和模型资产与3.1完全相同。
- 本地签名APK 40,628,215 bytes，SHA256 `d9140e5a25ba120e1a2ba7961db94c8b83a04d9eaab2ffbc19ebe84969b8d697`。最终交付以CI发布包及本页后续下载回验为准。
- `tools/check_apk.py`检查ARM64 ELF、manifest/dex和Nano/Small模型对通过；apksigner验证通过；16KiB zipalign通过。
- 固定测试证书SHA256 `fcd138196a9ef353dbe2e98a23ee22da0c6ed0a6cef442599d59d742c56dbbac`，与0.2/0.3/3.1相同，可覆盖安装；私钥不提交。
- aapt确认版本3.2.0/code5、包名 `io.github.lvshanyewu.wandervision`、仅ARM64。新增INTERNET；相机、fine/coarse位置保留，没有后台定位/相册全盘权限或后台服务。
- 禁明文网络，API使用系统默认TLS、不跟重定向；令牌仅私有偏好存储，备份/设备转移排除偏好与私有文件。浏览器URL只有设备ID，不带令牌。未把真实凭据写源码、日志或记录。

## 自动化契约检查

Android34/x86_64模拟器执行独立androidTest APK，18项通过，最后代码变动后已复跑。自定义Instrumentation不引入JUnit/AndroidX依赖；假传输不连接公网，使用目标应用内隔离的测试偏好/缓存，不改实际配对数据。

- 配对码格式、凭据长度/字符、固定浏览器源及不信任任意dashboard_url。
- 心跳空GPS、令牌仅作为传输认证参数；原JPEG Base64.NO_WRAP与原JSON保持不变、COCO类别不重命名。
- 同一UUID重试/重复回执，损坏哈希、4MiB/6MiB限制、有界读取、错误响应、401与超时错误脱敏。
- 旧配对响应/401不能覆盖或清除新连接；私有回执持久化且原JSON不改。
- 心跳发送前剔除过期GPS及内部字段，后台超过一个周期没有继续发，暂停状态不发。

初次测试使用Instrumentation APK自身上下文导致存储提交检查失败，已修正为目标应用隔离存储，随后16项、补生命周期后的18项均通过。这是测试环境修正，没有清除实际用户数据。

## 公网端到端验证

使用App真实界面配对PHONE-01，名称 **Android-Emulator-E2E-TEST**，Web显示在线并收到实际FPS；无有效GPS，location_valid=false。服务器可能保留示意lat/lon，不能当作有效定位。自有User-Agent的最终版本也再次确认心跳在线；宿主默认Python-urllib会被CDN1010拒绝，测试脚本改用明确UA，未放宽TLS。

- App快门生成真实本地JPEG/JSON后，在照片详情点击上传，server_event_id=`report-208ec932c3f1def0daf50552`，event_id=`ef0ce2a9-c69d-407b-b0b0-ff7f59efc63c`。
- 首次duplicate=false；再次及最终版本第三次点击后duplicate=true，attempts=3，服务器ID与received_at保持不变，档案只有一条。
- 公网采集档案标记test_data=true；GET原始JSON与本机完整对象相同，GET JPEG与本机字节相同。34645 bytes、480×751，SHA256 `8a245dd5ea6a22ab5ece2073a517061b640415b715ada8d6bc34a2220c556ea1`；JSON位置为null。
- 启用原地图入口，系统浏览器实际打开 `https://lvshanyewu.site/wandervision/?device=PHONE-01`，页面显示校园仪表盘与PHONE-01待定位。浏览器期间App心跳停止，返回冷启动后恢复；配对及回执在升级/重启后保留。
- 测试照片供Web对话核对并保留，未清空公共Demo数据库。未观察到wandervision进程崩溃；模拟器crash buffer中的早期goldfish-RIL崩溃不属于本App。

证据保留在忽略的 `artifacts/report-3.2-e2e.*`、`live-3.2-verification.json`、`ui-3.2-*.png/xml`。公网source=real表示实际设备HTTP通道，测试画面是模拟器合成输入且明确TEST；不是实体摄像头、GNSS或真实路况识别验收。

## 待真机确认

小米13覆盖安装、配对、移动网络/断网重试、定位质量、照片方向/非空目标框、前后台恢复和持续稳定性。没有新增病害模型、Turnip、实时视频或自动后台上传。操作与边界见[Android联动文档](android-monitor.md)、[版本说明](releases/3.2.0.md)。

## 最终发布包

- [标签CI](https://github.com/lvshanyewu/wandervision/actions/runs/39038186698)的构建、检查和发布步骤均成功；[3.2.0 Release](https://github.com/lvshanyewu/wandervision/releases/tag/v3.2.0)。
- [最终APK](https://github.com/lvshanyewu/wandervision/releases/download/v3.2.0/wandervision-3.2.0-arm64.apk)：40,627,999 bytes；SHA256 `895ad88178caacd5d3f295a042838697e75568ed76a11978f515c5fd4a899f28`。
- 已下载到本地 `artifacts/wandervision-3.2.0-arm64.apk`，与Release的SHA256SUMS一致；再次通过check_apk、apksigner、zipalign、aapt。证书仍是上述固定身份，版本3.2.0/code5、ARM64、相机/网络/前台位置权限。
- 回验时刻2026-10-17 21:54（Asia/Shanghai）。交付以标签CI产物为准，本地APK因构建环境不同hash不同。公开测试照片保留；测试模拟器已关闭，未清空服务器数据库。
