---
schema_version: 1
type: "continuation"
id: "android-experiments-3.4"
title: "3.4实验模型与固定机位规则批量接入"
module_id: "android"
status: "done"
next_step: "用户覆盖安装v3.4.0，从CPU/320逐个测试标志/垃圾/车牌拍照读号；固定机位再试ROI规则。依据小米13反馈保留或删除实验项。"
updated_at: "2026-10-19T22:05:28+08:00"
external_checks: [{"service": "GitHub Release", "status": "published", "checked_at": "2026-10-19T22:05:09+08:00", "evidence": "v3.4.0 https://github.com/lvshanyewu/wandervision/releases/tag/v3.4.0 APK与SHA256SUMS上传完成；API确认APK160158481字节与本机SHA256一致。源码0fc89a7；GitHub Actions runs39265272124/39265222013均success。"}]
---

# 用户授权

用户要求按老师选做范围调查可复用方案，随后授权将能独立移除的候选尽量加入App试用，保留原模型。

# 已完成

3.4.0/versionCode7新增BDD100K、标志36/21类、车牌定位、垃圾6类、TACO YOLO11s九类，加上原四模型共十检测器；选一个运行。车牌快门时同帧HyperLPR识别器转NCNN CPU候选读号，保留字典77/输出78差异拒绝策略。固定机位手选ROI停留/逆向/虚拟线、限定已知类别道路异物提示；模型/相机/后台切换关闭规则，一键恢复基线。可选720p采集失败回落。照片JSON扩展独立于Web，无Web修改。

模型公开包models-experiments-v1已发布，SHA256 c5195d3e5352695513f14d7be9be039a8928fadd6428e5007ad0c97d4d02add6。工具固定下载与哈希，原生/模型二进制不提交。

# 验证

最终模拟器55项合成契约/十模型原生检查通过。六模型18形状数值对照、OCR概率差0.0066656通过。模拟器拍照/JSON/JPEG同帧完整性check_report通过，未上传新照片。签名ARM64 assembleDebug lintDebug通过ADB MCP；check_apk、apksigner和16KiB zipalign通过。APK160158481字节，SHA256 6fc9a1dfa71db00d37a76b4e93c9f99b9b6d3814f90b254c69b5f6d36c08806c。证书沿用既有测试签名。

# 范围边界

docs/teacher-scope-feasibility.md逐项覆盖老师要求。积水权重无明确许可未打包；设施损坏/事故/特种车型/真实测速车高距离未实现。固定机位提示非违法判定，虚拟线非自动实线。手机性能/720p/GNSS/精度/读号由用户真机反馈；不可据模拟器宣称成功。
