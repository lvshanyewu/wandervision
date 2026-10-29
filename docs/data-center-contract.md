# 课设数据中心契约 · 2026-10-29

产品副标题：**道路巡检具身智能体**。定位是可展示、可整理、可导出的课程 Demo。

## Android 兼容

继续使用 `POST /api/v1/devices/PHONE-01/reports`（其余两个PHONE位同理）、独立Bearer、`{report:<完整schema1.0 JSON>,image_base64:<原始JPEG>}`。event_id幂等；原始报告/照片不改写，服务器会另外生成统一数据台账。原有时间/设备/位置质量/模型/模式/逐目标label,class_id,confidence,bbox/OCR/固定机位疑似规则均保留语义。

3.4.1可选新增顶层 `capture_context:{site_name:"采集点名称",note:"采集备注"}`，最多80/240字符。拍摄时冻结，新设置只影响之后新照片。Web读取可选字段，旧报告缺省为空；无GPS仍可上报，不由采集点名称伪造GPS。服务器接收成功额外返回 `record_id` 供后台定位；旧App可忽略新字段。

## Web 业务闭环

- 展示首页 `/`，后台台账 `/admin/`，可视化汇总报告 `/report/`。
- `GET /api/v1/records` 分页，支持source/status/category/priority/q/start/end筛选。
- `GET /api/v1/records/summary` 同筛选汇总：来源、审核、类别、趋势、地点和关注条目。
- `GET /api/v1/records/<id>` 单条结构化详情；原图存在时附带report_id及report_available。
- `POST /api/v1/records/review` 同源JSON：`{ids:[id],status?:pending|confirmed|rejected|resolved,priority?:normal|important|urgent,tags?:[标记],note?:审核备注,expected_versions?:{id:version}}`，批量最多100条，陈旧版本409、不部分更新。
- `GET /api/v1/records/export?format=csv|json` 与当前筛选一致，UTF-8 BOM CSV适配Excel，JSON含逐目标信息。
- `/report/?<筛选>` 自动汇总当前筛选数据，可浏览/打印保存PDF；`/api/v1/records/report?format=html` 下载独立HTML。

一份报告对应一条待复核业务记录，多个目标仍在该条目内，不把框数当独立病害数。路害/规则均是识别线索，审核是演示人工复核。SIM、TEST、REAL来源始终明确；不把模拟数据包装成真实AI结果。不接生产账号/权限或复杂工单系统。

台账最多3000条、30天，照片仍保持既有50张/96MiB/7天；图片清理后台账显示“仅保留条目”。清空Demo同时清理台账及审核记录。模拟复位保留台账历史，清库才删除。
