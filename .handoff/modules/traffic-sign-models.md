---
schema_version: 1
type: "module"
id: "traffic-sign-models"
title: "交通标志、路牌与车牌模型候选"
summary: "2026-10-19已下载静态核验四份标准YOLOv8候选：BDD100K10类、交通标志36/21类、单类车牌定位；国内精细标志可试PULC232类裁剪分类，完整中文车牌优先HyperLPR3。尚未转换/集成。"
aliases: []
tags: []
sources: ["docs/traffic-sign-model-feasibility.md", "docs/traffic-sign-model-candidates.json"]
dependencies: []
related_modules: ["android", "model-research"]
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-19T21:15:33+08:00"
---

## 已核查
完整报告docs/traffic-sign-model-feasibility.md与revision/SHA256/类别清单docs/traffic-sign-model-candidates.json。下载二进制仅在.deps/research/traffic-signs，无D盘根目录临时文件。四份权重与HF LFS哈希匹配，仅ZIP/pickletools静态检查，未执行pickle、导出或推理。

## 判断
低成本优先36类YOLOv8标志测试及BDD100K Nano道路场景对照；36类没有限速40，不能宣称中国全覆盖。21类另含信号灯颜色但无细分限速。PULC官方232类、224输入且权重可访问，需先检测裁剪再分类；YOLO-TS有TT100K45类下载入口但特殊单P2/stride4头与自定义模块，未核验网盘权重及完整分发许可，不是普通v8替换。

单类牌照YOLO仅定位，无号码。HyperLPR3有Android ARM64、Apache2，需MNN/OpenCV/16KiB核对；we0091234 v8plate有专门关键点输出且顶层许可不明确，暂不首选。地名文字另需OCR。

## 当前瓶颈与边界
ndkcamera当前采集640×480，输入640不能恢复远处标志/牌照细节；应实测高分辨率采集和ROI。不改变既有COCO/RDD，后续单选模式扩展独立类别/JSON model_id，先数值验证再用户真机。两个TT100K名义HF仓库当日没有实际权重，已排除即取即用候选。
