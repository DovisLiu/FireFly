# 流萤 (FireFly)

开源的 Android 影音播放应用，同时支持 **Android TV 大屏** 与 **手机** 两种使用场景。
本项目仅提供播放能力，不包含任何影音内容源，内容源由用户通过外部配置自行扩展。

> 本项目遵循 **GPL v3** 许可证（见 [LICENSE.md](LICENSE.md)）。

## 项目信息

| 项目 | 值 |
|------|-----|
| 应用名称 | 流萤 |
| 包名 | `com.firefly.tv` |
| 当前版本 | v1.0.0（versionCode 100）|
| 最低版本 | Android 7.0（API 24）|
| 原生架构 | `arm64-v8a`（64 位）、`armeabi-v7a`（32 位） |
| 界面变体 | `leanback`（电视版）、`mobile`（手机版）|

## 功能特性

- **点播**：多站点分类浏览与筛选；多站点并行搜索（关键字自动繁转简以提升兼容性）；播放失败自动换源（解析器 → 线路 → 搜索其他站 → 下一站点）；观看记录（保留 60 天）、收藏、无痕模式；电视版支持遥控器操作，手机版支持手势（亮度 / 音量 / 进度）、上下滑切集、屏幕旋转与锁定。
- **直播**：支持 M3U / TXT（`#genre#` 分组）/ JSON 三种直播源格式；XMLTV 的 EPG（支持 `.gz`，每 6 小时自动刷新）；追看 / 时移（`append`、`pltv` 等类型）；频道收藏、隐藏分组密码保护；TVBus、ForceTech 等特殊引擎。
- **播放器**：ExoPlayer（Media3）+ FFmpeg 软解，硬解 / 软解自动降级切换；SurfaceView / TextureView 渲染；Widevine、PlayReady、ClearKey DRM（支持 `#KODIPROP` 声明）；弹幕（与播放时间轴精确同步，支持远程推送）；SRT / SSA / ASS 外挂字幕、系统 CaptioningManager、远程实时注入；倍速、多缩放比例、画中画（PiP）、后台音频、片头 / 片尾自动跳过。
- **爬虫引擎**：支持三种语言编写爬虫 —— Java JAR（DexClassLoader）、JavaScript（QuickJS）、Python（Chaquopy）。
- **网络功能**：DoH（DNS over HTTPS，支持 Bootstrap IP）；HTTP / HTTPS / SOCKS4 / SOCKS5 代理（按 host 正则动态选择）；Hosts 解析覆盖（支持通配符 `*`）；CORS 注入（按 host 规则在响应中注入自定义头）；广告拦截黑名单；WebView 嗅探（regex 拦截媒体 URL，支持 UA 伪装）。
- **DLNA 投放**：DMC（投放端，手机版）扫描局域网 DLNA 设备并投放媒体；DMR（被投放端，电视版）作为 Renderer 接收其他设备投放。
- **Android Auto**：电视版支持 Android Auto，可在车机浏览播放记录与直播频道、进行播放控制。
- **远程控制**：应用启动后绑定本地 HTTP 服务器（NanoHTTPD），端口从 **9978** 自动探测至 **9998**，用于播放控制、推送字幕 / 弹幕、多设备同步等。完整端点说明见 [docs/LOCAL.md](docs/LOCAL.md)。
- **备份迁移**：支持一键导出备份分享、从文件导入，方便换设备迁移数据。

## 模块架构

```
├── app/            主应用（含两套 UI 变体：leanback 电视版 / mobile 手机版）
├── catvod/         爬虫抽象层（Spider 接口、OkHttp 网络栈）
├── quickjs/        QuickJS JavaScript 引擎
├── chaquo/         Chaquopy Python 引擎（爬虫用）
└── firefly6stub/   media3 编译桩（DolbyVisionOutputPolicy 等）
```

`app/src/main/` 为两个版本共用的业务逻辑，`app/src/leanback/` 与 `app/src/mobile/` 各自实现对应 UI。

## 配置说明

应用通过 Vod / Live 配置（JSON）加载内容源，详见以下文档：

| 文档 | 说明 |
|------|------|
| [docs/CONFIG.md](docs/CONFIG.md) | Vod / Live 配置完整字段说明 |
| [docs/SPIDER.md](docs/SPIDER.md) | 爬虫所有方法规格与返回格式 |
| [docs/LOCAL.md](docs/LOCAL.md) | 本地 HTTP API 所有端点完整说明 |
| [docs/LIVE.md](docs/LIVE.md) | 直播源格式完整说明 |

## 许可证

本项目遵循 **GPL v3** 许可证。详见 [LICENSE.md](LICENSE.md)。
