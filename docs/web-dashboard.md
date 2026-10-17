# Web 道路巡检监控中心

生成日期：2026-10-17 · 版本：web-demo-v1

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
- 重庆主城区真实道路/河流矢量地图，支持拖动、缩放、回中、标记点击、路线高亮和事件定位。
- 在线深色 / 原色街道地图可切换；瓦片失败自动回到本地矢量底图。不需要地图 Key。
- 车辆沿 3 条预先规划道路往返移动；按距离插值，端点折返，不随机瞬移。
- 日常场景每约 8 秒生成 1 条检测事件；演示场景每约 4 秒生成 1 条。同设备同类别最少间隔 45 秒。
- 第 22–44 秒固定哨点 CQ-F06 离线并恢复（100 秒周期）；第 60–80 秒 CQ-V04 停靠（140 秒周期）。停止期间不产生检测与模拟心跳，状态事件由监控系统记录。
- 设备/位置搜索、设备类型筛选、事件分类筛选、单设备巡检记录、设备详情、检测趋势和事件分布。
- 暂停、继续、重置、切换场景。控制作用于服务器的共享演示，其他访问者也会看到同一状态。重置只重置模拟部分。
- 页面每 3 秒轮询；后台页降低至 12 秒。地图标记在两次快照间平滑移动；断开服务显示旧数据与重连提示。
- 首次有 24 条明确标注 SIM 的演示历史，便于课程展示；累计数据指本轮模拟加数据库中保留的真实事件，趋势为最近 6 个 5 分钟桶。

全部模拟设备、模拟事件和合成示意画面均明确标记 **SIM**；真实接口上报由服务端固定标记 **REAL**。预留手机的地图坐标仅供布局，`location_valid=false` 表示尚未收到真实 GPS。当前没有真实视频、真实手机连接或云端 AI 推理，不能据此宣称 Android 实机摄像头或模型已验证。

## 地图来源与可用性

默认底图使用随仓库分发的重庆 OSM 数据快照（约 3.8 MB 原始 JSON，部署代理启用 gzip），由 Leaflet Canvas 绘制。范围约 WGS84 经度 106.43–106.66、纬度 29.49–29.66；保留主干道、次干道、住宅道路、江河与主要地标标签，适合本 Demo 范围，不是完整导航地图或测绘成果。

数据于 2026-10-17 经 Overpass API 获取，三条路线经 OSRM 获取后固定到本地。运行时不调用路线规划服务，也不需要下载地图瓦片才能工作。所有数据采用 WGS84；未来接入高德/腾讯等 GCJ-02 服务应在适配层转换，不能直接混用。

在线街道图为 [OpenStreetMap Standard](https://operations.osmfoundation.org/policies/tiles/)，浏览器只请求用户当前视野内的瓦片，保留 Referer 和可见署名，遵守浏览器缓存；不预下载、不打包在线瓦片。该免费服务无可用性保证。默认本地矢量保证弱网演示；如后续使用 Google、CARTO 或商业地图，应另行配置合法 Key、授权和额度。选择当前方案是为了无需账号、无付费依赖且可离线回退。

更新数据需显式运行 `python tools/prepare_web_maps.py`，会联网访问固定版本 Leaflet、Overpass 与 OSRM。这不是启动或部署前置步骤。Leaflet 版本 1.9.4，下载时核对官方 JS/CSS SHA-256；数据和第三方许可证见 `web/static/data/SOURCES.md`、`THIRD_PARTY_NOTICES.md`。

## 接口契约 v1

本地基址 `http://127.0.0.1:8090`，线上基址 `https://lvshanyewu.site/wandervision`。

| 方法与路径 | 作用 | 鉴权 |
| --- | --- | --- |
| `GET /api/v1/health` | 进程探活 | 公开 |
| `GET /api/v1/snapshot` | 设备、最近 200 条事件、统计、最近 30 分钟趋势 | 公开演示 |
| `POST /api/v1/simulation` | 暂停、继续、重置、场景 | 同源 JSON、限流 |
| `POST /api/v1/devices/PHONE-01/heartbeat` | GPS、状态与指标 | 对应设备 Bearer token |
| `POST /api/v1/devices/PHONE-01/events` | 检测事件（支持三个 PHONE ID） | 对应设备 Bearer token |

服务器没有配置设备密钥时返回 `503`，不使用默认令牌。为后续联调，在服务器 `/etc/wandervision.env`（root 所有、权限 600）配置：

```text
WV_DEVICE_TOKENS={"PHONE-01":"<独立随机令牌至少24字符>","PHONE-02":"<另一个独立令牌至少24字符>","PHONE-03":"<第三个独立令牌至少24字符>"}
```

令牌需用安全随机数生成，每个设备不同，不放进前端、Git 或日志。配置后只重启 `wandervision` 服务。HTTP 请求头使用 `Authorization: Bearer <该设备令牌>` 和 `Content-Type: application/json`。

心跳 body（建议每 10 秒一次）：

```json
{"lat":29.563,"lon":106.578,"battery":86,"fps":24,"speed":0,"status":"online"}
```

必需：有限数字 `lat[-90,90]`、`lon[-180,180]`。可选：`battery[0,100]`、`fps[0,240]`、`speed[0,200]`（km/h）、`status=online|paused`、有时区的 `observed_at`。设备最后接收时间以服务器为准；超过 30 秒未心跳自动显示离线。未上报指标显示 `—`。

事件 body：

```json
{"event_id":"inspection-0001","kind":"pothole","confidence":0.89,"observed_at":"2026-10-17T01:00:00Z"}
```

- `event_id` 必需，1–80 位字母/数字/下划线/连字符，同设备同 ID 幂等，重试返回 `duplicate=true`；第一次内容为准。
- `kind=pothole|crack|vehicle`，`confidence` 为有限数字 `[0,1]`。
- `observed_at` 必需 ISO 8601 且包含时区，允许过去 24 小时至未来 5 分钟。上例日期仅演示格式，联调须改为当前实际采集时间。
- 可选独立 `lat/lon`，缺省使用最近 GPS。必须先收到有效在线心跳；离线或暂停状态不接受新检测。
- 客户端 `source` 不被信任，服务端固定为 `real`；设备、事件、趋势和详情共用前端模型。
- `preview_url` 当前固定为空；视频会话、预览图片上传、断网队列、人工处置与事件分页留到下一阶段。
- 请求最大 16 KiB，每源地址最多 120 次写入/分钟；不开放跨域 CORS。反向代理后的地址限流由代理聚合，后续真实多设备接入应在可信代理边界加入更细的按设备限流。

响应码：`200` 成功（含幂等重试）、`400` 参数/格式错误、`401` 令牌错误、`403` 跨站模拟控制、`413` 超过大小、`415` 错误媒体类型、`429` 限流、`503` 未配置设备令牌。

本版是公开演示站，读接口会公开后续接收的位置/事件。接真实个人手机前应增加查看权限和数据保留策略；当前线上只运行模拟数据，未写入真实个人位置。

## 测试

```sh
python -m unittest discover -s web/tests -v
python -m py_compile web/server.py web/deploy/deploy.py tools/prepare_web_maps.py
node --check web/static/app.js
```

22 项行为测试覆盖模拟设备/路由连续性、离线/暂停、冷却、统计、SQLite 持久化、真实来源与幂等、字段范围、鉴权、同源控制、安全响应头、目录访问限制，以及定向 Nginx 配置补丁。Node 只用于语法检查，不是运行依赖。浏览器手工冒烟覆盖筛选、地图/设备选择、详情、暂停/继续、说明弹窗、分析视图和 390px 响应式布局。

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

不修改当前 0.3.0 APK。后续先加入运行时 GPS 权限、WGS84 定位和本地事件队列，再对接本版心跳与幂等事件接口，配置每设备令牌和 HTTPS。现有 COCO 车辆输出可先联调；坑槽/裂缝模型需独立训练/移植与用户真机验证。最后再评估低频预览图片或实时视频传输，避免先引入昂贵的视频服务器。
