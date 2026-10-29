---
schema_version: 1
type: "continuation"
id: "android-data-center-3.4.1"
title: "3.4.1采集说明与数据汇集中心兼容"
module_id: "android"
status: "done"
next_step: "用户覆盖安装3.4.1，设置采集点/备注后拍照并手动上传，在Web待复核台账核对完整条目和幂等；Web部署与台账联调由Web对话负责。"
updated_at: "2026-10-29T15:33:53+08:00"
external_checks: [{"service": "GitHub Release / CI", "checked_at": "2026-10-29T15:33:41+08:00", "status": "published", "evidence": "v3.4.1已发布 https://github.com/lvshanyewu/wandervision/releases/tag/v3.4.1；API确认APK160158481字节且digest与本地aac56f7873043369e077af6f8f085fd409d700e1b4e95b6fac9cd750b8707959一致。源码56d5b10，CI40835484912/40835460704成功；UI拍照说明、JSON/JPEG/EXIF哈希检查与23项quick契约通过。"}]
---

# 实现

用户在Web对话2026-10-29明确授权App小改；维持schema1.0完整report+原JPEG和event_id幂等。3.4.1/versionCode8副标题道路巡检具身智能体；设置采集点与备注，仅后续快门时冻结capture_context.site_name/note（80/240 Unicode码点），照片两行缩略、详情/JSON完整保留。原模型/定位/逐目标label/class_id/confidence/bbox、OCR和疑似规则继续整份上传，无接口或检测链路改动。

# 验证

签名ARM64 assembleDebug lintDebug经ADB MCP成功；check_apk、aapt版本、apksigner固定签名、16KiB zipalign通过。x86_64轻量23项数据/上传契约通过，使用隔离假传输无公网照片上传。APK160158481字节，SHA256 aac56f7873043369e077af6f8f085fd409d700e1b4e95b6fac9cd750b8707959。细节见docs/android-data-center-3.4.1.md和docs/build-verification-3.4.1.md。

# 协调

已回Web对话01a1475e-6d00-7133-a70e-a7d935641833字段契约与预计版本，允许可选字段读取；App不改Web/部署。真实小米13与Web台账联动验收由用户反馈，不宣称成功。
