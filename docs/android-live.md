# Android 3.3 LIVE 与手机面板

## 用户操作
在工具中配对，点击取景器左上LIVE。首次开启显示公开演示用途，后续点击直接开/关。只在前台工作，进入后台、切模型/镜头/输入/计算单元、暂停/解除连接会关闭，返回不会偷偷重开。地图入口在App内打开只读手机监控页；本地统计保留核心快照。

## 传输实现
JNI在已经画框的取景帧上每80ms最多采样一次，缩至长边640，单槽覆盖；Java读取后JPEG55，不包含界面按钮。独立线程用默认可信TLS套接字、HTTPS主机名校验和HTTP/1.1分块连续写4字节大端长度+JPEG；仅持一帧、一个写连接，TCP背压时不再取帧，2.5秒写入看门狗断开。55秒或14MiB轮换，零长度终止并读取HTTP回执，用户开启且仍前台时重连。无后台Service、录像或磁盘队列。

每个会话使用随机UUID，经X-WV-Stream-Id传入；独立线程约2秒读取公开状态，只在stream_id匹配且live时显示服务端接收FPS，不能以本机编码速度假冒接收速度。停止先禁用采样、取消连接，再尽力POST stop；服务端epoch和TTL处理晚帧/断网。路径与限额见android-live-contract.md。

最后原生HTTP/1.1流的持续帧率仍待小米13验收；Web侧已用合成JPEG验证约12FPS接收，不能代替App实测。

公网约480ms RTT下，最初逐帧POST实测只有1.7–1.8FPS，故替换为长连接。10–15FPS为目标，取决于本机识别帧率、编码、带宽和背压；Small/640可能低于Nano。模拟器画面标TEST，不能替代小米13实测。

## 手机地图与安全边界
固定https://lvshanyewu.site/wandervision/mobile/?device=PHONE-01；URL不含Bearer。WebView只读、无JavaScript桥；关闭file/content访问、网页定位及混合内容，导航只接受同源wandervision路径，不绕过TLS错误。JavaScript仅供可信手机页面地图/状态交互。关闭面板销毁WebView；相机和前台心跳继续。

## 模型
保留COCO Nano/Small，新增RDD2022 Nano/Small；路害类别编号属于该模型：0纵裂、1横裂、2龟裂、3坑洞。模式/原图/JSON使用稳定model_id和对应label，车辆COCO类别不映射成路害。权重来源/哈希见road-models-manifest.json，转换及动态输入数值对照见export_road_models.py、check_road_export.py和road-models-export-verification.json。模型由prepare_native.py调用prepare_models.py校验安装，二进制不提交Git。
