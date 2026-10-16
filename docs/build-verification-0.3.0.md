# 0.3.0 验证与交付记录

日期：2026-10-16（Asia/Shanghai）。应用源标签 `v0.3.0`，提交 `d7179412b59bf7f2df715a823ae703a307bc27b0`。

## 最终发布文件

- [GitHub Release](https://github.com/lvshanyewu/wandervision/releases/tag/v0.3.0)；[APK下载](https://github.com/lvshanyewu/wandervision/releases/download/v0.3.0/wandervision-0.3.0-arm64.apk)。
- 40,560,931 bytes；SHA-256：`33c767e769da81d96cb8c3d1b2d206bc30c8d8fda3e47839818ad607158b4922`。
- 已从Release下载到本地 `artifacts/wandervision-0.3.0-arm64.apk`，与发布的SHA256SUMS一致。
- 专用测试证书SHA-256：`fcd138196a9ef353dbe2e98a23ee22da0c6ed0a6cef442599d59d742c56dbbac`，与0.2.0相同，可覆盖安装。私钥未提交。

## 构建与包检查

- Windows通过ADB MCP完成最终ARM64 `assembleDebug lintDebug --offline`。Lint 0错误、20条非阻塞警告；未屏蔽错误。
- [最终标签CI](https://github.com/lvshanyewu/wandervision/actions/runs/38923288434)构建、lint、包检查和签名检查成功，自动发布。
- 对下载的最终APK执行 `tools/check_apk.py`、`apksigner verify --print-certs`、`zipalign -c -P 16 -v 4`，均通过。
- aapt确认版本0.3.0/3、包名 `io.github.lvshanyewu.wandervision`、仅ARM64、只请求CAMERA权限。
- 模型资产没有改动，继续提供Nano和Small；C++改动仅为检测框/标签配色，未改阈值、输出解析或相机方向。

## UI冒烟检查

另用x86_64测试构建，在Android34、1080×2400、420dpi模拟器检查。交付APK仅含ARM64。

- 竖屏、横屏布局；修正初版底部说明被裁切的问题，检查参数、快门和导航均可见。
- 设置、模型选择、仪表盘采用统一原生黑灰黄样式；没有有效检测或纯取景时置信度/检测数据正确显示“—”。
- 黄色快门保存截图，回读包含模拟相机画面与界面；截图列表显示真实缩略图和已有记录，保留查看与分享入口。
- 上述流程未观察到AndroidRuntime崩溃。模拟相机彩色块是测试源，不是应用背景或真机检测证据。
- 本地证据保留在 `artifacts/ui-0.3-portrait.png`、`ui-0.3-landscape.png`、`ui-0.3-settings.png`、`ui-0.3-dashboard.png`、`ui-0.3-records.png`，不提交构建/测试产物。

## 用户验收边界

用户已反馈0.2.0“功能性上没有任何问题”。本轮仅更新视觉，没有增加模型、GPS、地图、轨迹、后端或Web；地图/轨迹按钮禁用占位。0.3.0尚未收到小米13真机反馈，重点验证字体、横竖屏安全区、控件遮挡、黄色标注可读性和覆盖升级后的截图记录。测试重点见[发布说明](releases/0.3.0.md)。
