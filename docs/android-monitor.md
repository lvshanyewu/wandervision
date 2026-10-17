# Android 监控中心联动（3.2.0）

## 手机操作

1. 网页右上设置 → 手机连接，为PHONE-01/02/03生成6位一次性配对码（5分钟）。App工具 → 监控中心连接，填写配对码和可选名称，点击“连接并启用前台心跳”。
2. 仅在App前台且连接启用时约每10秒发送位置/电量/FPS；无有效定位也可在线。可暂停连接或解除本机连接。位置权限仍为可选，未配对不会自动请求上传或心跳。
3. 快门取景 → 记录 → 照片详情 → 一键上传。按钮旁说明照片/识别结果/位置会进入**公开课程Demo**，可以公开查看，演示数据库可随时被网页清空。照片默认只保存本地，未自动批量上传。
4. 上传后显示服务器事件编号和接收时间；失败保留原图/JSON，可用同一记录手动重试。再次点击已确认的同一记录不会生成新事件。原图或原JSON被移除、原图被编辑改变哈希时拒绝上传，不重新压缩或伪造校验和。
5. 原地图占位入口已启用为网页仪表盘；本机统计仍保留。打开系统浏览器，配对后带 `?device=PHONE-01`。离开App会暂停其心跳，返回恢复；浏览器不接收设备令牌。无GPS时网页只能显示待定位，不能据示意坐标断言真实位置。

3.1旧截图/巡检记录仍可查看和导出；只有具有原始JSON的巡检照片提供上传。旧照片页脚“未上传”是生成时状态，以详情中的服务器回执为准。Demo删除远程照片后，本机旧回执不自动变成未上传：它显示“上次服务器确认”，重新配对并重试可再确认。

## 固定契约

基址：`https://lvshanyewu.site/wandervision`。App只使用HTTPS、系统证书验证，API不跟随重定向，不支持用户自定义任意服务器地址。客户端User-Agent为wandervision/3.2.0；测试脚本须使用明确UA，宿主默认Python-urllib可能被CDN以1010拒绝。

- `POST /api/v1/pair`：`{code,name?}`，接收device_id、token。码不持久化，令牌存App私有SharedPreferences。输入和响应限制长度/字符，令牌不进入URL、原始JSON、源码、日志或发布记录。服务端返回的dashboard_url不直接用于导航，App自行从固定源构造设备页地址。
- `POST /api/v1/devices/PHONE-xx/heartbeat`：Bearer认证，`{lat,lon,battery?,fps,speed?,status:"online"}`。WGS84坐标只有新鲜且非模拟位置才发送；过期/未授权/无位置/系统mock/已知模拟器为null。速度从有效位置的m/s转km/h，未知时省略，电量不可用也省略。发送前复核位置年龄，网络排队超10秒的心跳跳过，不补发历史队列。
- `POST /api/v1/devices/PHONE-xx/reports`：Bearer，`{report:<完整原始schema1.0对象>,image_base64:<原JPEG Base64.NO_WRAP>}`。最多4MiB图片/6MiB请求；先验证SHA256。GPS或先行心跳都不是上传前提，原记录的mock/stale质量标记原样传递。COCO类别/model_id不改成坑槽裂缝。
- 成功响应：ok、duplicate、server_event_id、received_at；同event_id重试使用同一UUID，服务器幂等。接收端控制保留/配额，不保证永久存储。请求10秒连接/30秒读取超时，失败由用户重试。认证失败解除对应旧连接；旧请求不能清除新配对。

## 本地持久化与生命周期

MonitorApi负责固定源、有界读写、默认TLS、请求/回执校验；MonitorStore负责私有会话与AtomicFile回执；MonitorLink使用独立网络线程，避免阻塞NCNN推理队列。心跳每次至多一个在途请求，onPause取消后续调度，不启动后台定位/前台服务；已发出的请求及用户手动开始的上传可完成。暂停连接同时停止心跳与新的上传。

`files/reports/<UUID>.json`及相册原JPEG不可变；上传状态存在 `files/uploads/<UUID>.json`，包括设备/服务器ID、接收时间、重复确认、尝试次数和最后错误，不包含token。原始记录中的sync.state不代表当前回执。应用清除数据/卸载会移除私有令牌、JSON和回执；备份/设备转移规则排除私有文件和偏好设置。解除本机连接不删除本地照片或服务器数据。不存在断网自动恢复上传、后台服务或复杂可靠队列。

## 已验证与边界

18项Android Instrumentation契约检查使用隔离测试设置/缓存与假传输，涵盖校验、同UUID、原数据不变、空GPS、大小/哈希/错误、凭据更新竞争、回执、前后台和暂停。运行 `assembleDebug assembleDebugAndroidTest -PtestAbi=x86_64` 并在模拟器执行 `am instrument -w io.github.lvshanyewu.wandervision.test/com.tencent.yolov8ncnn.ContractInstrumentation`；测试APK不得作为手机交付。

另已在模拟器真实界面配对PHONE-01（Android-Emulator-E2E-TEST）、收到实际HTTPS心跳、拍照、上传并再次上传、读取公网页面。报告 ef0ce2a9-c69d-407b-b0b0-ff7f59efc63c，服务器 report-208ec932c3f1def0daf50552，重复标记true且仅一条档案；公网原始JSON对象和JPEG字节与本机相同，test_data=true，坐标为空。App通过系统浏览器打开带设备参数的校园仪表盘。

上述仅证明软件/网络链路。测试相机画面是模拟器合成输入，Web的source=real表示设备HTTP通道，test_data=true明确排除真实路况验证；不是实体小米13摄像头、GNSS或检测质量通过。真机网络环境、位置、照片方向、持续稳定性仍交由用户安装反馈。
