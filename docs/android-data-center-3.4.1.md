# Android 3.4.1 数据汇集兼容

Android沿用schema_version=1.0和既有POST reports的report对象+image_base64原JPEG接口。MonitorLink从私有原JSON读取整份report，MonitorApi原样发送，按event_id重试幂等；照片SHA核验与回执分离逻辑保留。没有将逐目标结果压成计数，也没有把车辆/垃圾类改成路害。

既有字段：captured_at、captured_at_epoch_ms、source设备信息、location（status/坐标或null/accuracy_m/age_ms等）、inference（model_id/mode/vehicle_filter/每个detections的class_id,label,confidence,bbox_xywh_normalized）、plate_recognition候选、experimental_analysis疑似规则、image字节/哈希/取景矩形。OCR与规则不存在时不补造结果，GPS缺失不伪造坐标。

新增可选顶层字段：

```json
{"capture_context":{"site_name":"校门","note":"夜间巡检"}}
```

两个字符串分别最多80/240个Unicode码点，空说明为空字符串。设置只影响后续拍照；快门时在主线程冻结偏好，原JSON创建时加入，不允许在上传时覆盖旧证据。照片附两行说明（过长省略），完整文本在JSON和详情保留。原有无capture_context的历史记录继续支持。Web可按可选字段读取并与其余报告字段映射待审核台账；审核状态不回写原始采集事实。本轮不改服务端或部署。

轻量验证使用ContractInstrumentation的quick=true，仅跑数据/上传契约，不重复十模型推理矩阵；新增检查包含Unicode截断、空/历史字段、OCR/规则/采集说明随完整JSON原样发送。ARM64 assembleDebug lintDebug、check_apk、apksigner和zipalign作为交付检查；手机实际相机/位置/准确率仍待用户安装反馈。
