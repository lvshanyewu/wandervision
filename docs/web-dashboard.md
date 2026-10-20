# Web 道路巡检监控中心

生成日期：2026-10-17 · 版本：web-demo-v3（LIVE 与手机仪表盘）

在线地址：[https://lvshanyewu.site/wandervision/](https://lvshanyewu.site/wandervision/)

## 本地启动

要求 Python 3.11+，运行时无需安装第三方包，无前端构建步骤。在仓库根目录执行：

```sh
python web/server.py
```

浏览器访问 <http://127.0.0.1:8090/>。`Ctrl+C` 停止。改端口：`python web/server.py --port 8091`；需要局域网访问时加 `--host 0.0.0.0`。

真实设备数据默认写入 `web/runtime/wandervision.sqlite3`（已忽略）；`--db` 或 `WV_DB` 可指定其他路径。模拟状态保存在内存中，重启/重置会重新开始；真实设备及事件会保留。SQLite 文件使用 WAL，备份应使用 SQLite backup API，不能只复制运行中的主文件。

## 可以直接验收的功能

- 13 个设备位：6 个固定哨点、4 台移动巡检车、3 个 `PHONE-01..03` 真实手机预留位。
- 以重庆交通大学南岸校区为中心的道路、河流、校园与建筑地图，支持拖动、缩放、回中、标记点击、路线高亮和事件定位。
- 在线深色 / 原色街道地图可切换；瓦片失败自动回到本地矢量底图。不需要地图 Key。
- 车辆沿 学府大道北线、交大南侧、汇龙路 3 条预先规划道路往返移动；按距离插值，端点折返，不随机瞬移。
- 日常场景每约 8 秒生成 1 条检测事件；演示场景每约 4 秒生成 1 条。同设备同类别最少间隔 45 秒。
- 第 22–44 秒固定哨点 CQ-F06 离线并恢复（100 秒周期）；第 60–80 秒 CQ-V04 停靠（140 秒周期）。停止期间不产生检测与模拟心跳，状态事件由监控系统记录。
- 设备/位置搜索、设备类型筛选、事件分类筛选、单设备巡检记录、设备详情、检测趋势和事件分布。
- 暂停、继续、重置、切换场景。控制作用于服务器的共享演示，其他访问者也会看到同一状态。重置只重置模拟部分。
- 页面每 3 秒轮询；后台页降低至 12 秒。地图标记在两次快照间平滑移动；断开服务显示旧数据与重连提示。
- 首次有 24 条明确标注 SIM 的演示历史，便于课程展示；累计数据指本轮模拟加数据库中保留的真实事件，趋势为最近 6 个 5 分钟桶。

全部模拟设备、模拟事件和合成示意画面均明确标记 **SIM**；真实接口上报由服务端固定标记 **REAL**。预留手机的地图坐标仅供布局，`location_valid=false` 表示尚未收到真实 GPS。当前支持仅内存最新JPEG的LIVE预览，没有编码视频或云端 AI 推理，App接入流程已实现但用户真机采集仍待验证，不能据此宣称 Android 实机摄像头或模型已验证。

## 地图来源与可用性

初次打开优先在线深色 OSM 街道图，失败自动回退随仓库分发的校园 OSM 矢量快照（约 0.6 MB，1610 个要素，部署代理启用 gzip），由 Leaflet Canvas 绘制。展示范围约 WGS84 经度 106.535–106.615、纬度 29.465–29.535，校园附近附带建筑、步道和大学边界；不是完整导航地图或测绘成果。地图中心 WGS84 (29.4903657, 106.567229)，取自 OSM 大学边界 way/412677871 的中心，默认 zoom15。学校官网确认南岸校区地址为学府大道66号：https://zs.cqjtu.edu.cn/contact 。

数据于 2026-10-17 经 Overpass API 获取，三条路线经 OSRM 获取后固定到本地。运行时不调用路线规划服务，也不需要下载地图瓦片才能工作。所有数据采用 WGS84；未来接入高德/腾讯等 GCJ-02 服务应在适配层转换，不能直接混用。

在线街道图为 [OpenStreetMap Standard](https://operations.osmfoundation.org/policies/tiles/)，浏览器只请求用户当前视野内的瓦片，保留 Referer 和可见署名，遵守浏览器缓存；不预下载、不打包在线瓦片。该免费服务无可用性保证。本地矢量回退保证弱网演示；如后续使用 Google、CARTO 或商业地图，应另行配置合法 Key、授权和额度。选择当前方案是为了无需账号、无付费依赖且可离线回退。

更新数据需显式运行 `python tools/prepare_web_maps.py`，会联网访问固定版本 Leaflet、Overpass 与 OSRM。这不是启动或部署前置步骤。Leaflet 版本 1.9.4，下载时核对官方 JS/CSS SHA-256；数据和第三方许可证见 `web/static/data/SOURCES.md`、`THIRD_PARTY_NOTICES.md`。

## 接口契约 v1（保留兼容）

本地基址 `http://127.0.0.1:8090`，线上基址 `https://lvshanyewu.site/wandervision`。

| 方法与路径 | 作用 | 鉴权 |
| --- | --- | --- |
| `GET /api/v1/health` | 进程探活 | 公开 |
| `GET /api/v1/snapshot` | 设备、最近 200 条事件、统计、最近 30 分钟趋势 | 公开演示 |
| `POST /api/v1/simulation` | 暂停、继续、重置、场景 | 同源 JSON、限流 |
| `POST /api/v1/devices/PHONE-01/heartbeat` | GPS、状态与指标 | 对应设备 Bearer token |
| `POST /api/v1/devices/PHONE-01/events` | 检测事件（支持三个 PHONE ID） | 对应设备 Bearer token |

尚未配对且没有配置设备密钥时返回 `503`，不使用默认令牌。推荐使用下方V2一次性配对；如需兼容固定令牌，可在服务器 `/etc/wandervision.env`（root 所有、权限 600）配置：

```text
WV_DEVICE_TOKENS={"PHONE-01":"<独立随机令牌至少24字符>","PHONE-02":"<另一个独立令牌至少24字符>","PHONE-03":"<第三个独立令牌至少24字符>"}
```

令牌需用安全随机数生成，每个设备不同，不放进前端、Git 或日志。配置后只重启 `wandervision` 服务。HTTP 请求头使用 `Authorization: Bearer <该设备令牌>` 和 `Content-Type: application/json`。

心跳 body（建议每 10 秒一次）：

```json
{"lat":29.563,"lon":106.578,"battery":86,"fps":24,"speed":0,"status":"online"}
```

`lat/lon` 可同时为null或省略，设备可在线但标为未定位；提供坐标时两者必须同时是有限数字，范围 `lat[-90,90]`、`lon[-180,180]`。可选：`battery[0,100]`、`fps[0,240]`、`speed[0,200]`（km/h）、`status=online|paused`、有时区的 `observed_at`。设备最后接收时间以服务器为准；超过 30 秒未心跳自动显示离线。未上报指标显示 `—`。

事件 body：

```json
{"event_id":"inspection-0001","kind":"pothole","confidence":0.89,"observed_at":"2026-10-17T01:00:00Z"}
```

- `event_id` 必需，1–80 位字母/数字/下划线/连字符，同设备同 ID 幂等，重试返回 `duplicate=true`；第一次内容为准。
- `kind=pothole|crack|vehicle`，`confidence` 为有限数字 `[0,1]`。
- `observed_at` 必需 ISO 8601 且包含时区，允许过去 24 小时至未来 5 分钟。上例日期仅演示格式，联调须改为当前实际采集时间。
- 可选独立 `lat/lon`，缺省使用最近 GPS。必须先收到有效在线心跳；离线或暂停状态不接受新检测。
- 客户端 `source` 不被信任，服务端固定为 `real`；设备、事件、趋势和详情共用前端模型。
- `preview_url` 在照片上报后指向最近上传的原图，不能当作实时视频。可靠离线队列、视频会话、人工处置和事件分页留到后续。
- 此兼容心跳/事件接口请求最大 16 KiB（新增照片报告为6MiB），每源地址最多 120 次写入/分钟；不开放跨域 CORS。反向代理后的地址限流由代理聚合，后续真实多设备接入应在可信代理边界加入更细的按设备限流。

响应码：`200` 成功（含幂等重试）、`400` 参数/格式错误、`401` 令牌错误、`403` 跨站模拟控制、`413` 超过大小、`415` 错误媒体类型、`429` 限流、`503` 未配置设备令牌。

本版是公开演示站，读接口会公开后续接收的位置/事件。接真实个人手机前应增加查看权限和数据保留策略；线上为模拟设备与明确标记的TEST联调上传，用户真实手机采集仍待验证。

## 测试

```sh
python -m unittest discover -s web/tests -v
python -m py_compile web/server.py web/deploy/deploy.py tools/prepare_web_maps.py
node --check web/static/app.js
```

48 项行为测试覆盖模拟设备/路由连续性、离线/暂停、冷却、统计、SQLite 持久化、真实来源与幂等、字段范围、鉴权、同源控制、安全响应头、目录访问限制，以及定向 Nginx 配置补丁。Node 只用于语法检查，不是运行依赖。浏览器手工冒烟覆盖筛选、地图/设备选择、详情、暂停/继续、说明弹窗、分析视图和 390px 响应式布局。

## 既有服务器部署

沿用 myseoul 的 `startpage` Nginx，新增单一路径与独立 systemd 服务，部署时不覆盖 Compose、不运行全量恢复、不重启既有 Python 服务。

本地部署脚本依赖 Paramiko，可执行 `python -m pip install paramiko`。从安全环境提供 `DEPLOY_HOST`、`DEPLOY_PORT`、`DEPLOY_USER`、`DEPLOY_PASSWORD`（或已有 SSH key/agent）。主机必须已在 known_hosts 登记并核对指纹；脚本拒绝未知主机。随后在仓库根目录：

```sh
python web/deploy/deploy.py --check
python web/deploy/deploy.py
```

脚本会核对生产挂载、命名主站和空闲端口，读取实际 Docker 网关，只绑定该内网地址的 `5065`；创建 `/opt/wandervision/releases/<UTC时间>/`，逐文件读回核对字节，设置 `current` 符号链接并启用 `wandervision.service`。服务采用 DynamicUser、128 MB 内存与 25% CPU 配额，真实库位于 systemd StateDirectory `/var/lib/wandervision/`。

修改前把 Nginx、已有 unit 和文件哈希保存在 `/opt/wandervision/backups/<UTC时间>/`；只在主站新增 `/wandervision/` 反代，并检查 `nginx -t` 后 reload。Nginx 是单文件 bind mount，配置写入保持原 inode。会检查并发配置变更；失败回退本服务。原站静态文件和数据库不写入。

部署完成另行经公网 HTTPS 验证页面、静态资产和健康/快照接口。不要把本机服务响应当作公网可用证明。

回退使用部署输出的精确备份目录：

```sh
python web/deploy/deploy.py --rollback /opt/wandervision/backups/<UTC时间>
```

若 Nginx 在部署后又有其他改动，脚本会拒绝覆盖，应对照备份只移除对应块。首次部署回退移除代理并禁用本服务；升级回退恢复前版 release/unit。数据库、代码发布目录及备份始终保留，不进行用户数据回滚。

## Android 下一阶段

Android3.1已具备前台GPS/WGS84、独立巡检JPEG和schema1.0 JSON；本轮由独立App对话实现3.2的仪表盘、配对、前台心跳和手动照片上传/重试。后续再完善持久可靠队列及访问权限。现有 COCO 车辆输出可先联调；坑槽/裂缝模型需独立训练/移植与用户真机验证。已加入最新JPEG实时预览，后续再评估编码视频传输，避免先引入昂贵的视频服务器。


## 校园版新增：设置、配对与采集档案

右上角设置分四页：演示场景、手机连接、数据与存储、显示偏好。五个预设为日常、高峰、病害、断线、夜间。显示偏好支持地图源、10/20FPS节能切换、跟随选中巡检车和事件标记，保存于当前浏览器。

点击设备预览可展开透视 Canvas 场景：固定路口交叉车流、信号灯交替；移动巡检弯道、建筑、树、车辆、周期坑槽/裂缝和识别示意框。支持校园慢速场景、日间/夜间/雨天及局部暂停。全部是合成循环，不执行AI、不上传、不写采集档案。后台页停止动画，服务器不渲染视频。

手机联动：

1. 网页设置 → 手机连接，选择 PHONE-01..03，生成一次性6位配对码（5分钟有效）。
2. App 3.2.0 的监控中心连接页输入该码，令牌保存在 App 私有设置。
3. App 前台每约10秒心跳；允许没有GPS的在线状态，不填造坐标。仪表盘入口打开固定HTTPS页面，可携带 `?device=PHONE-01`。
4. 拍照后在详情点击一键上传，上传原始JPEG和schema1.0 JSON；服务器确认后单独保存回执，不改写原始报告中的local_only和照片文字。
5. 网页“采集档案”显示照片、采集时间、定位质量、同帧目标数、SHA256和完整原始JSON。照片不是实时视频。模拟器型号或mock/test_data标记的上传显示TEST，不当成真实路况验证。

新增接口（线上前缀仍是 `/wandervision`）：

| 方法/路径 | 作用 |
| --- | --- |
| `GET /api/v1/settings` | 配额与空间、校园中心、支持预设 |
| `POST /api/v1/pairing` | 同源设置操作：`{"device_id":"PHONE-01"}` 返回code和expires_at |
| `POST /api/v1/pair` | App：`{"code":"六位码","name":"可选设备名"}` 返回device_id、token、dashboard_url |
| `POST /api/v1/devices/PHONE-01/reports` | Bearer鉴权，原始JSON+JPEG Base64上传 |
| `GET /api/v1/reports` | 最近50个报告摘要 |
| `GET /api/v1/reports/<server_event_id>` | 原始报告JSON |
| `GET /api/v1/reports/<server_event_id>/image` | 原始JPEG，no-store |
| `POST /api/v1/maintenance` | 同源设置操作：clear清空，compact按配额清理并压缩 |

配对令牌只在成功pair时返回一次，服务器只存SHA256散列；同接入位重新配对轮换旧令牌。已有环境配置令牌仍兼容，清理配对不会移除环境变量中的静态令牌。配对码请求有同源检查，兑换最多12次/分钟（Demo全局）且一次性，错误/过期码400。

上传请求：`{"report":<完整报告对象>,"image_base64":<Base64.NO_WRAP原始JPEG>}`，单图最多4MiB，metadata最多256KiB，总body最多6MiB；只允许一张图并行接收，忙时429可重试。要求schema_version="1.0"、合法event_id、image.sha256；校验JPEG SOF尺寸/合理像素上限和原图哈希。照片不要求GPS或先心跳，设备无定位时保持location_valid=false。重复同设备event_id且相同图哈希返回同一server_event_id、duplicate=true；不同图复用ID拒绝。

成功回执：`{"ok":true,"duplicate":false,"server_event_id":"report-...","received_at":"ISO8601","image_url":"HTTPS URL"}`。API不会根据COCO车辆报告生成坑槽/裂缝；上传统一生成“巡检照片”事件。没有GPS的报告不画假事件位置；测试位置质量和原始模型字段保留。

### 数据体积与一键清理

SQLite新增credentials（最多3设备散列）、reports（原始metadata和JPEG BLOB）；不会把照片写入其他站点目录。每次上传和每小时巡检清理：最多7天、50张照片、96MiB图片内容、5000条真实设备事件；移除超额照片时对应照片事件一并移除。模拟事件只在内存中，最多600条。

设置中的“一键清空演示数据库”立即执行，不二次确认：删除全部本Demo照片/设备事件/设备状态，模拟从零开始；默认保留配对以便继续拍摄，也可取消勾选使配对失效。不会删除手机本地文件或其他项目数据。SQLite在清理后commit、checkpoint、VACUUM、再次checkpoint，回收主库及WAL占用，并报告实际释放空间。少量空表/索引/配对所占空间仍保留；自动配额删除后的空闲页由SQLite复用，手动压缩会物理收缩。部署备份不包含用户图片，清库不承诺可恢复。

照片/位置在此公开Demo中可被查看和清空。真正保存个人巡检数据前需另加查看权限/角色控制；当前用户已明确授权本课程Demo使用可随时丢弃数据。后台不得声称真实手机摄像头、GNSS或AI病害效果已验证。

### V2 检查

48项自动测试包括真实HTTP配对→无GPS心跳→超过16KiB的JPEG上传→重复上传→原图字节读回→清库404与实际空间回收，以及配对持久化/expiry、7天/数量配额和旧基线。浏览器检查设置、预设、图片/原始JSON、隔离测试库清理、循环场景与响应式。Android构建/实机边界由App对话记录，不能用Web测试代替APK检查。


## LIVE 与手机仪表盘（2026-10-18）

手机专用只读页面：https://lvshanyewu.site/wandervision/mobile/?device=PHONE-01 。仅保留核心计数、校园地图、设备切换、所选设备状态与LIVE，适合App内WebView；不需要令牌URL、网页定位/相机权限或JS桥接。地图失败回退本地OSM；桌面右上角也有“手机简版”入口。

App3.3配对后手动开启LIVE，设备端完成检测框绘制与JPEG压缩，单请求在途，目标10–15FPS，实际受网络往返与编码性能影响。服务器每设备15帧/秒、每帧256KiB、1280最大边/921600像素，不进行视频编码、推理或帧存档。真实设备详情点击预览可展开LIVE；巡检照片仍通过采集档案查看。

最新帧2秒无更新即移除显示，5秒后由模拟时钟线程释放内存，即使没有观众或暂停模拟也执行。手动关闭/后台由App尽力发送stop；重配、清库清除帧。LIVE为设备通道，模拟器/模拟输入标TEST LIVE；与SIM合成循环明确区分。浏览器优先原生MJPEG连续观看（全局最多6连接），失败时顺序读取最新JPEG并用ETag避免重复传输，回退观看上限10FPS、隐藏页面停止轮询，不积压历史帧，Blob URL及时释放。

精确接口与客户端限制见[android-live-contract.md](android-live-contract.md)。新HTTP测试覆盖设备鉴权隔离、15FPS窗口、超过120次/分钟仍可持续上传、原图/ETag/410、短TTL物理释放、停止/重配/清库、大小与并发限制、无SQL写入/无假GPS、简版页面，以及RDD原始label/class_id不按COCO重解释。

跨境逐帧POST实际约1.7–1.8FPS，故追加 `/live/stream` 长连接：HTTP chunked中依次发送4字节BE长度/JPEG，零长度结束；无逐帧ACK，仅最新帧。每会话60秒/16MiB，App提前轮换，UUID stream_id用于确认本轮接收；stop/重配/清库epoch拒绝旧会话晚帧。仅三条精确Nginx stream路由关闭请求缓冲和启用HTTP1.1，其他服务/API不变。参照[Nginx官方请求缓冲说明](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_request_buffering)。另有原生MJPEG原图/停止结束/并发限额测试。新增chunk分片/截断/总量、上传未结束即可GET图片、会话ID、停止后晚帧拒绝等测试，共48项。
