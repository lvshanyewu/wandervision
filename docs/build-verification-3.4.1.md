# 3.4.1 交付检查

2026-10-29，versionCode8。沿用现有模型与NCNN检测链路，本轮新增采集说明及完整条目上传提示。

- 经ADB MCP执行签名ARM64 assembleDebug lintDebug --offline通过，日志在本机artifacts/mcp-3.4.1.log。
- x86_64测试包构建通过，API34模拟器quick=true契约检查23项通过：包含新增采集说明Unicode边界、旧记录兼容、OCR/规则/说明随完整JSON原样上传，以及既有上传哈希、幂等与配对契约。使用隔离假传输，没有公网照片上传。
- tools/check_apk.py通过，十检测器/OCR/字典哈希完整，单ARM64 ELF。aapt确认versionName3.4.1/versionCode8。
- apksigner verify v2通过，证书SHA256 fcd138196a9ef353dbe2e98a23ee22da0c6ed0a6cef442599d59d742c56dbbac，沿用既有测试签名；zipalign -c -P 16 4通过。
- APK160158481字节，SHA256 aac56f7873043369e077af6f8f085fd409d700e1b4e95b6fac9cd750b8707959。

没有重复十模型矩阵或做性能长测。小米13相机、定位、实景精度及Web最新台账交互等待用户安装反馈；本次契约测试只证明客户端完整字段与图片传输结构保留，不宣称新Web已部署验收。
