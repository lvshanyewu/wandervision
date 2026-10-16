---
schema_version: 1
type: "continuation"
id: "camera-ui-0.2"
title: "0.2.0 专业手持识别仪界面与双模型"
module_id: "android"
status: "open"
next_step: "完成发布文档和Git提交；CI构建固定测试签名的v0.2.0 Release并下载核验。"
updated_at: "2026-10-16T22:26:33+08:00"
---

# 用户基线
0.1.0 已获用户正常启动、夜间车辆检测可用反馈；新UI和预览方向改动需重新真机确认。
# 已实现
专业暗色仪器UI、液晶实时状态、金属参数键与立体快门；Nano保留+上游同源Small；通用/车辆/纯取景；截图到相册、最近30张查看/分享；FPS/推理耗时/当前帧目标快照与趋势；网格、镜头、许可面板；GPS/云同步明确未接入。
# 适配与测试
小米13官方1080x2400/20:9。模拟器同尺寸x86_64已实际启动：Nano与Small切换、横屏右侧控件、竖屏恢复、纯取景无推理耗时、仪表盘、截图写入MediaStore并持久化记录。未发现AndroidRuntime崩溃。模拟器FPS不代表手机性能。
# 构建
Windows ARM64通过编译/lint；最终固定签名版本经ADB MCP重新构建中/待核对。私钥在本机非仓库目录，CI加密Secrets提供；本轮不提交签名文件。云端后端/Web仍未开发。
