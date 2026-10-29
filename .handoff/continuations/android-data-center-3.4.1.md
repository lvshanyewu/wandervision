---
schema_version: 1
type: "continuation"
id: "android-data-center-3.4.1"
title: "3.4.1采集说明与数据汇集中心兼容"
module_id: "android"
status: "open"
next_step: "源码56d5b10已推送、CI成功；模拟器UI填写采集点/备注→拍照→JSON/JPEG校验通过。v3.4.1 Release APK首传网络超时，正在重试（本地完整签名APK可用）。上传后回填发布结果并通知Web。"
updated_at: "2026-10-29T15:30:52+08:00"
---

# 实现

用户在Web对话2026-10-29明确授权App小改；维持schema1.0完整report+原JPEG和event_id幂等。3.4.1/versionCode8副标题道路巡检具身智能体；设置采集点与备注，仅后续快门时冻结capture_context.site_name/note（80/240 Unicode码点），照片两行缩略、详情/JSON完整保留。原模型/定位/逐目标label/class_id/confidence/bbox、OCR和疑似规则继续整份上传，无接口或检测链路改动。

# 验证

签名ARM64 assembleDebug lintDebug经ADB MCP成功；check_apk、aapt版本、apksigner固定签名、16KiB zipalign通过。x86_64轻量23项数据/上传契约通过，使用隔离假传输无公网照片上传。APK160158481字节，SHA256 aac56f7873043369e077af6f8f085fd409d700e1b4e95b6fac9cd750b8707959。细节见docs/android-data-center-3.4.1.md和docs/build-verification-3.4.1.md。

# 协调

已回Web对话01a1475e-6d00-7133-a70e-a7d935641833字段契约与预计版本，允许可选字段读取；App不改Web/部署。真实小米13与Web台账联动验收由用户反馈，不宣称成功。
