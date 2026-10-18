# Android / Web LIVE 契约 v1

2026-10-18。固定基址 `https://lvshanyewu.site/wandervision`，沿用配对所得设备 Bearer。实时通道与巡检报告分开；帧仅在内存中，最多三台手机各一帧，不写 SQLite 或磁盘。

## 推荐：连续长连接上传（跨境网络）

`POST /api/v1/devices/PHONE-01/live/stream`，沿用Bearer；`Content-Type: application/x-wandervision-live`；必填 `X-WV-Stream-Id: <UUID>`（该轮LIVE开关的随机UUID）；GET状态/快照live的stream_id回显最新帧所属会话，仅匹配本次UUID且state=live才确认本机在播。HTTP `Transfer-Encoding: chunked`，**不得设置 Content-Length**。App `HttpURLConnection.setChunkedStreamingMode(8192)`，每帧写4字节unsigned big-endian长度，再写完整JPEG并flush。检测框已经在JPEG中。测试输入仍发 `X-WV-Test-Data: 1`。

每帧大小/尺寸及15FPS上限同下方单帧接口。单会话 **60秒或16MiB总body** 上限；App应在 **55秒或14MiB** 主动轮换，写4字节零结束标记后close输出流（让HTTP发终止chunk），再读200 JSON回执。回执含frames、dropped、reason及最新LIVE元数据。长连接内没有逐帧HTTP ACK，不等回执再取下一帧；自然TCP背压下阻塞写时不再取帧，只保留当前一帧。建议640长边JPEG55、目标12.5FPS。

同一设备仅允许一个写通道，其他请求429/Retry-After1；旧流断开后最长5秒读超时释放占用。超频帧丢弃不积压，连续超频超过30帧将断开连接。单会话/传输异常可在LIVE仍由用户启用且App在前台时短暂退避重连；手动关闭/后台/解除配对不得重连。停止时先禁止采样并disconnect，再尽力发 `/live/stop`。服务端epoch在stop/重配/清库时递增，旧会话后到帧不能恢复直播；每帧重新验证token。正常会话轮换不主动清最后一帧，TTL继续生效。每次网络读空闲超过5秒关闭连接；最长会话计时60秒，阻塞读额外最多5秒。

Nginx只针对三条精确stream路由关闭request/response buffering、启用proxy HTTP1.1、16MiB body与75秒响应超时；其他API保留原6MiB限制。需要验证公网代理能在连接未结束时持续收到帧，不能仅凭最终200宣称实时。

## 兼容：单帧上传 / 停止

- `POST /api/v1/devices/PHONE-01/live`（支持 PHONE-01..03）
- `Authorization: Bearer <配对令牌>`；`Content-Type: image/jpeg`；必须 `Content-Length`，原始 JPEG 二进制，不是 JSON/Base64/multipart。
- 最大 **262144 bytes**；每边最大 **1280px**，总像素最多 **921600**。建议长边 640–960px、JPEG quality 45–65；已经带框的取景帧，不需要再次推理。
- 可选 `X-WV-Test-Data: 1`，模拟器/模拟输入必须发送，网页标为 TEST LIVE。省略或 `0` 表示实际设备通道。
- 每台设备最多 **15 次/秒**（按服务器滑动1秒窗口，无IP共享120/min限制）；最多一个上传请求在途，失败丢帧，不补传。服务器忙/限频返回429，`Retry-After: 1`；建议降到10FPS或间隔重试，401/403停止并要求重配。
- 成功200 JSON：`{"ok":true,"device_id":"PHONE-01","frame_id":1,"received_at":"ISO8601","fps":12.4,"state":"live","max_fps":15}`。frame_id为服务器单调序号，不依赖App序号；单次上传不是持久化巡检报告。
- `POST /api/v1/devices/PHONE-01/live/stop`，同Bearer，`Content-Type: application/json`，body `{}`。立即删除该设备内存帧，200 `{"ok":true,"state":"stopped"}`。手动关闭、进入后台、解除连接时尽力调用；即使请求失败，TTL仍会断流。令牌重配和清库也会撤销对应实时帧。
- 413大小、415格式、400无效JPEG/尺寸、401鉴权、503未配对。Content-Length不足/读取超时不会接受半帧。

## 公开只读展示

- `GET /api/v1/devices/PHONE-01/live`：`{ok,device_id,state,frame_id,received_at,age_ms,fps,width,height,bytes,test_data,max_fps}`。
- `state`: `idle`尚未开始；`live`最近2秒收到帧；`stale`超过2秒但未到5秒；`expired`超过5秒（JPEG已丢弃）；`stopped`主动停止。过期元数据仅保留用于说明状态。
- `GET /api/v1/devices/PHONE-01/live/frame.jpg`：仅LIVE时200 image/jpeg、no-store，`ETag`及`X-WV-Frame-Id`；`If-None-Match`相同时304。非LIVE返回410 JSON，网页应移除旧图，不将冻结图片冒充直播。
- snapshot 的真实device附带 `live` 相同元数据，不包含JPEG/Base64。帧上传不代替heartbeat，不修改GPS、模拟事件或巡检档案。
- `GET /api/v1/mobile` 为手机端精简快照，只有 server_time/campus/stats/devices 和最近3条 events，不传完整照片档案/趋势。
- 手机页面固定 **`/mobile/?device=PHONE-01`**，同源公开只读，不在URL带token。只显示核心计数、校园地图、设备状态和所选手机LIVE；可内嵌WebView，也可系统浏览器打开。无需登录或WebView JS桥接，不请求网页GPS/相机权限。

App必须由用户显式点击LIVE开关，仅前台工作，清楚提示公开Demo画面；UI显示实际发送FPS。10–15FPS是目标，受手机编码/网络往返影响，禁止排队积压或声称保证帧率。现有照片上传/报告与配对接口保持兼容。

## 报告模型兼容

沿用 schema1.0 的 `inference.model_id/mode/detections`。展示读取每个检测的 `label`，不将 class_id 当作统一COCO编号。RDD2022模型 `rdd2022-yolov8n/s` 可上报 mode=road_damage、四类 longitudinal_crack/transverse_crack/alligator_crack/pothole。Web保留原始JSON及JPEG，中文标签只是显示翻译，不对模型效果作保证，也不从实时画面自动生成持久病害事件。
