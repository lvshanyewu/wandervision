# 巡检记录契约 v1（3.1.0）

每次手动取景产生：系统相册JPEG + 应用私有目录 `files/reports/<event_id>.json`。图片和JSON可通过系统分享导出，JSON另有系统文件选择器保存入口。本版没有INTERNET/后台定位权限，没有自动上传、服务端确认或自动去重。JSON的`sync.state=local_only`明确表示未上传，不等于已有可靠上传队列。

## 字段

| 字段 | 含义 |
| --- | --- |
| schema_version / event_id / app_version | 契约版本1.0、UUID、生成版本 |
| captured_at / captured_at_epoch_ms | 本帧推理/标注完成时刻，UTC ISO8601及Unix毫秒；使用当前墙钟与单调时钟差还原，不是硬件曝光时刻 |
| source | 本次安装生成的随机installation_id、厂商/机型/API；用于区分记录来源，不读取IMEI，清除数据后ID会更换，不替代服务器设备认证 |
| capture_elapsed_ns | Android CLOCK_BOOTTIME / elapsedRealtimeNanos，单次设备开机内有效，不能作为跨设备时间 |
| camera | 前后镜头、frame_id、本帧宽高、display_upright、native_viewfinder_frame来源 |
| inference | enabled、模型ID、模式、输入、请求/实际CPU或Vulkan、driver、显示FPS/推理耗时/最高置信度、同帧detections |
| inference.detections[] | COCO class_id、英文label、confidence、bbox_xywh_normalized；框以camera宽高为基准，不是报告图片全图 |
| location | WGS84坐标、状态、权限精度、水平精度m、fix时间/单调时间、age_ms、provider、系统mock标记；有数据才填高度/速度/方向 |
| image | JPEG文件名、格式、尺寸、字节数、SHA256、EXIF是否写入、是否烘焙识别框、无UI按钮、viewfinder_rect_px |
| sync | local_only、attempts=0、server_event_id=null；后续服务端接收可用event_id作为幂等键，图片校验和核对附件 |
| uri / name / time / model / mode / size / objects / fps | 安卓本机相册关联与历史列表兼容字段；content URI仅在原设备有效，不能当服务器图片地址 |

`viewfinder_rect_px=[left,top,width,height]`给出相机画面在报告JPEG中的矩形。若后续重新绘制框，x=left+bbox.x*width，y=top+bbox.y*height；图片已有烘焙框，避免重复画。JSON可独立于图片的可见文字使用完整精度坐标、全部检测对象。JSON不包含JPEG本体；上传时应采用JSON+二进制附件，不能传本地URI期待网页能读到。

## 帧与照片的一致性

快门请求下一个处理帧，JNI在同一推理锁下复制该帧已绘制的RGB、目标数组、统计和采样时刻，再生成JPEG报告。正常预览不持续复制RGB。等待帧超时2秒返回错误，不拿旧帧冒充新照片。取景来源仍是当前预览裁切后的帧，不是手机高像素静态拍照；JSON保留真实尺寸。保存期间禁止切模型/输入/模式/镜头，避免配置串帧。

位置取自快门请求时已有的最新有效样本，并计算到该帧的单调时钟年龄；它不是曝光瞬间精确同步的GNSS测量，行驶时必须结合age_ms与accuracy_m判断。FPS为本帧时的平滑显示帧率；目标数为当前帧，不是去重事件计数。

## 定位状态

- `fresh`：精确授权、样本年龄≤30秒；`approximate`：仅近似授权、年龄≤30秒。近似定位仍可能有较大误差，应读accuracy_m。
- `stale`：样本超过30秒，照片与UI标过期；JSON保留坐标与年龄供分析，**不写GPS EXIF**。
- `off` / `permission_denied` / `provider_disabled` / `searching` / `unavailable`：无本次可用坐标，经纬度等填null，不填0，不伪造上次位置。
- 接受缓存样本最多2分钟；定位仅前台运行，GPS/系统network请求约10秒、距离阈值0，实际回调取决于系统与信号。拒绝/关闭定位仍可识别和拍照；可在设置重新授权。
- `mock`是Android提供的标记，不是防伪证明；模拟器geo fix可能不带mock标记。开发机所有模拟定位都不是实测道路位置。
- 新鲜或近似坐标写入JPEG GPS EXIF，照片可见文字也含位置/精度/年龄。分享照片会连同这些位置一并分享。
- WGS84不是国内地图常用GCJ-02，监控中心若使用高德/腾讯底图需要在边界明确转换；不要覆盖原始坐标系声明。

## 存储与升级

JPEG通过MediaStore.pending写入、添加EXIF并回读计算SHA256；JSON通过AtomicFile持久化后发布照片，最后写最近列表索引。失败会清理本次图片和JSON。最近列表保留30条，**不是JSON保留上限**；旧JSON仍在应用目录，当前不自动清理。应用清除数据/卸载会移除私有JSON；需要长期保留应先导出。进程崩溃可能留下未完成MediaStore条目或未索引的JSON，不等同事务型数据库；接入自动上传前应加恢复扫描、存储配额、数据库与可靠队列。

旧版截图仍可查看/分享，没有JSON和位置的旧记录不补造新字段。分享Provider不导出、只读、限制UUID文件名，通过系统临时Uri授权；文件选择器导出由用户决定目标目录。相机/定位权限之外不请求全盘相册或后台位置权限。

开发核验：`python tools/check_report.py <export.json> <photo.jpg>`，检查时间、框、定位状态、照片尺寸/哈希。真实定位、拍照方向、持续使用与接收端分享支持由小米13安装反馈。
