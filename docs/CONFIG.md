# 配置说明

本文件说明 Vod（点播）与 Live（直播）配置档案的 JSON 结构与各栏位意义。

---

## 目录

- [Vod 配置（VodConfig）](#vod-配置vodconfig)
    - [顶层栏位](#顶层栏位)
    - [sites — 点播来源](#sites--点播来源)
    - [parses — 解析规则](#parses--解析规则)
    - [lives — 直播来源](#lives--直播来源)
- [Live 配置（LiveConfig）](#live-配置liveconfig)
    - [顶层栏位](#顶层栏位-1)
    - [groups — 频道分组](#groups--频道分组)
    - [channel — 频道项目](#channel--频道项目)
- [共用栏位物件](#共用栏位物件)
    - [doh — DNS over HTTPS](#doh--dns-over-https)
    - [proxy — 代理伺服器](#proxy--代理伺服器)
    - [rules — 网路拦截规则](#rules--网路拦截规则)
    - [headers — 注入回应标头](#headers--注入回应标头)
    - [hosts — DNS 解析覆盖](#hosts--dns-解析覆盖)
    - [ads — 广告过滤](#ads--广告过滤)
    - [catchup — 追看/时移](#catchup--追看时移)
    - [style — 卡片样式](#style--卡片样式)
- [完整范例](#完整范例)

---

## Vod 配置（VodConfig）

Vod 配置为一个 JSON 物件，作为应用程式的主要配置入口。配置可透过 URL、本地路径或直接贴入字串的方式载入。

### 顶层栏位

| 栏位          | 类型              | 说明                                                             |
|-------------|-----------------|----------------------------------------------------------------|
| `spider`    | `string`        | 全局 Spider JAR 路径或 URL，提供给所有 `sites` 作为预设爬虫。支援相对路径（`./`、`../`）。 |
| `wallpaper` | `string`        | 桌布图片或影片的路径/URL。支援静态图、GIF、影片格式。                                 |
| `logo`      | `string`        | 应用程式 Logo 图片路径/URL。                                            |
| `notice`    | `string`        | 启动公告文字，将显示给使用者。                                                |
| `sites`     | `array<Site>`   | 点播来源清单。详见 [sites](#sites--点播来源)。                               |
| `parses`    | `array<Parse>`  | 影片解析规则清单。详见 [parses](#parses--解析规则)。                           |
| `lives`     | `array<Live>`   | 直播来源清单。详见 [lives — 直播来源](#lives--直播来源)。                        |
| `doh`       | `array<Doh>`    | DNS over HTTPS 设定清单。详见 [doh](#doh--dns-over-https)。            |
| `proxy`     | `array<Proxy>`  | 代理伺服器设定清单。详见 [proxy](#proxy--代理伺服器)。                           |
| `rules`     | `array<Rule>`   | 网路拦截规则清单。详见 [rules](#rules--网路拦截规则)。                           |
| `headers`   | `array<Header>` | 针对特定主机注入 HTTP 回应标头。详见 [headers](#headers--注入回应标头)。             |
| `hosts`     | `array<string>` | DNS 解析覆盖规则。详见 [hosts](#hosts--dns-解析覆盖)。                       |
| `flags`     | `array<string>` | 平台标示旗标，用于标记特殊平台处理（如 `"qq"`）。                                   |
| `ads`       | `array<string>` | 广告域名过滤清单，符合的请求将被拦截。详见 [ads](#ads--广告过滤)。                       |
| `danmaku`   | `string`        | 弹幕 API URL，用于自动搜寻弹幕。详见 [danmaku](#danmaku--弹幕-api)。            |

---

### sites — 点播来源

`sites` 为 `Site` 物件阵列，每个物件代表一个点播来源。

| 栏位            | 类型              | 说明                                        |
|---------------|-----------------|-------------------------------------------|
| `key`         | `string`        | 来源唯一识别码，作为主键使用，不可重复。                      |
| `name`        | `string`        | 来源显示名称。                                   |
| `type`        | `integer`       | 来源类型，决定呼叫方式。详见下表。                         |
| `api`         | `string`        | API 端点 URL 或爬虫类别名称（如 `csp_Push`）。         |
| `ext`         | `string`        | 额外扩充资料，传入爬虫 `init()` 使用。可为字串、JSON 物件或路径。  |
| `jar`         | `string`        | 指定此来源使用的 Spider JAR 路径/URL，覆盖全局 `spider`。 |
| `click`       | `string`        | 点击拦截处理 URL 或规则。                           |
| `playUrl`     | `string`        | 播放 URL 前缀或转换规则。                           |
| `hide`        | `integer`       | `1` 表示在来源列表中隐藏此项目。                        |
| `timeout`     | `integer`       | 请求逾时秒数，覆盖预设值。                             |
| `searchable`  | `integer`       | 搜寻开关。`0`=停用，`1`=启用（预设）。                   |
| `changeable`  | `integer`       | 线路切换开关。`0`=停用，`1`=启用（预设）。                 |
| `quickSearch` | `integer`       | 快速搜寻开关。`0`=停用，`1`=启用。                     |
| `indexs`      | `integer`       | 索引模式旗标。`1` 表示此来源作为索引来源使用。                 |
| `categories`  | `array<string>` | 显示的分类名称白名单，仅显示清单中的分类。                     |
| `header`      | `object`        | 此来源请求时附加的 HTTP 标头，格式为键值对。                 |
| `style`       | `Style`         | 卡片显示样式。详见 [style](#style--卡片样式)。          |

**`type` 可选值：**

| `type` | `api` 格式                              | 说明                                                               |
|--------|---------------------------------------|------------------------------------------------------------------|
| `0`    | HTTP URL                              | 直接 GET 请求，回传 XML 格式（`ac=videolist`）。                             |
| `1`    | HTTP URL                              | 直接 GET 请求，回传 JSON 格式，额外支援 Filter 筛选参数（`f=`）。                     |
| `3`    | `csp_ClassName` / `xxx.js` / `xxx.py` | 爬虫直接呼叫：JAR（DexClassLoader）、JavaScript（QuickJS）、Python（Chaquopy）。 |
| `4`    | HTTP URL                              | 同 `1`，`ext` 扩充参数以 Base64 编码传递（`ext=`）。                           |

**范例：**

```json
{
  "key": "my_source",
  "name": "我的来源",
  "type": 3,
  "api": "csp_XYZ",
  "ext": "./ext.json",
  "searchable": 1,
  "changeable": 1,
  "timeout": 30,
  "header": {
    "User-Agent": "Mozilla/5.0"
  },
  "style": {
    "type": "rect",
    "ratio": 1.33
  }
}
```

---

### parses — 解析规则

`parses` 为 `Parse` 物件阵列，定义影片 URL 的解析处理规则。

| 栏位           | 类型              | 说明                                       |
|--------------|-----------------|------------------------------------------|
| `name`       | `string`        | 解析器显示名称。                                 |
| `type`       | `integer`       | 解析器类型。详见下表。                              |
| `url`        | `string`        | 解析 API 端点 URL，通常以待解析的影片 URL 作为后缀参数。      |
| `ext`        | `Parse.Ext`     | 解析器扩充设定物件。                               |
| `ext.flag`   | `array<string>` | 适用旗标清单，标示此解析器适用的平台（如 `["qq", "iqiyi"]`）。 |
| `ext.header` | `object`        | 解析请求时附加的 HTTP 标头，格式为键值对。                 |

**`parses.type` 可选值：**

| `type` | 说明                                   |
|--------|--------------------------------------|
| `0`    | 嗅探（WebView 拦截媒体 URL）                 |
| `1`    | JSON API（GET 请求，取回应的 `url` 栏位）       |
| `2`    | JSON 扩展（将所有 type=1 的解析器合并后送入 JAR 处理） |
| `3`    | JSON 聚合（将所有解析器资讯合并后送入 JAR 处理）        |
| `4`    | 超级解析（自动并行尝试所有 type=0/1 的解析器）         |

**范例：**

```json
{
  "name": "通用解析",
  "type": 1,
  "url": "https://api.example.com/parse?url=",
  "ext": {
    "flag": [
      "qq",
      "youku"
    ],
    "header": {
      "Referer": "https://www.example.com/"
    }
  }
}
```

### lives — 直播来源

Vod 配置中的 `lives` 栏位用于指向外部直播配置或内嵌直播资讯。每个物件为一个 `Live` 来源，栏位定义与 [Live 配置顶层栏位](#顶层栏位-1) 相同。

常见用法为指定 `url` 指向外部 `live.json`，或直接内嵌 `groups` 频道资料。

---

## Live 配置（LiveConfig）

Live 配置可以是独立的 JSON 档案，或内嵌于 Vod 配置的 `lives` 阵列中。

独立的 `live.json` 顶层支援以下栏位：

| 栏位        | 类型              | 说明                                              |
|-----------|-----------------|-------------------------------------------------|
| `spider`  | `string`        | 全局 Spider JAR 路径，提供给所有 `lives` 作为预设爬虫。          |
| `lives`   | `array<Live>`   | 直播来源清单，栏位定义见下表。                                 |
| `proxy`   | `array<Proxy>`  | 代理设定，同 Vod 配置。详见 [proxy](#proxy--代理伺服器)。        |
| `rules`   | `array<Rule>`   | 拦截规则，同 Vod 配置。详见 [rules](#rules--网路拦截规则)。       |
| `headers` | `array<Header>` | 注入标头，同 Vod 配置。详见 [headers](#headers--注入回应标头)。   |
| `hosts`   | `array<string>` | DNS 覆盖规则，同 Vod 配置。详见 [hosts](#hosts--dns-解析覆盖)。 |
| `ads`     | `array<string>` | 广告过滤清单，同 Vod 配置。详见 [ads](#ads--广告过滤)。           |

### 顶层栏位

`lives` 阵列中每个 `Live` 物件的栏位：

| 栏位         | 类型             | 说明                                                                                                  |
|------------|----------------|-----------------------------------------------------------------------------------------------------|
| `name`     | `string`       | 直播来源名称，作为主键使用，不可重复。                                                                                 |
| `url`      | `string`       | 外部直播列表 URL（M3U、TXT 或 JSON 格式）。与 `groups` 二择一。                                                       |
| `api`      | `string`       | 直播 API 端点或爬虫类别名称。                                                                                   |
| `ext`      | `string`       | 传入直播爬虫的额外扩充资料。                                                                                      |
| `jar`      | `string`       | 此直播来源使用的 Spider JAR 路径/URL。                                                                         |
| `click`    | `string`       | 点击拦截处理 URL 或规则。                                                                                     |
| `logo`     | `string`       | 频道预设 Logo 图片 URL，支援 `{id}`、`{name}`、`{logo}` 变数替换。                                                  |
| `epg`      | `string`       | EPG 节目表 URL，多个以逗号分隔。支援 `{id}`、`{name}`、`{epg}` 变数替换；含 `xml`/`gz` 的条目作为 XMLTV 来源，含 `{` 的条目作为 API 范本。 |
| `ua`       | `string`       | 预设 User-Agent 字串。                                                                                   |
| `origin`   | `string`       | 请求 `Origin` 标头值。                                                                                    |
| `referer`  | `string`       | 请求 `Referer` 标头值。                                                                                   |
| `timeZone` | `string`       | 时区设定，用于 EPG 时间显示（如 `"Asia/Taipei"`）。                                                                |
| `timeout`  | `integer`      | 请求逾时秒数。                                                                                             |
| `header`   | `object`       | 此来源请求时附加的 HTTP 标头，格式为键值对。                                                                           |
| `catchup`  | `Catchup`      | 追看/时移设定，作为此来源所有频道的预设值。详见 [catchup](#catchup--追看时移)。                                                 |
| `groups`   | `array<Group>` | 直播频道分组清单（内嵌方式）。详见 [groups](#groups--频道分组)。                                                          |
| `boot`     | `boolean`      | 是否在应用启动时自动选中此直播来源。多个来源设定时，最后一个生效。                                                                   |
| `pass`     | `boolean`      | `true` 表示略过密码保护，强制显示此来源所有分组（含设有密码的隐藏分组）。                                                            |

**范例：**

```json
{
  "name": "我的直播",
  "url": "./live.m3u",
  "epg": "https://epg.example.com/api?id={id},https://epg.example.com/xmltv.xml.gz",
  "ua": "Mozilla/5.0",
  "timeZone": "Asia/Taipei",
  "boot": true
}
```

---

### groups — 频道分组

`groups` 为 `Group` 物件阵列，将频道组织为分组显示。

| 栏位        | 类型               | 说明                                      |
|-----------|------------------|-----------------------------------------|
| `name`    | `string`         | 分组显示名称。                                 |
| `pass`    | `string`         | 分组密码，设定后需输入密码才能浏览此分组。                   |
| `channel` | `array<Channel>` | 此分组下的频道清单。详见 [channel](#channel--频道项目)。 |

**范例：**

```json
{
  "name": "新闻台",
  "channel": [
    {
      "name": "TVBS新闻台",
      "urls": [
        "http://example.com/tvbs.m3u8"
      ]
    },
    {
      "name": "民视新闻",
      "urls": [
        "http://example.com/ftv.m3u8"
      ]
    }
  ]
}
```

---

### channel — 频道项目

`channel` 为 `Channel` 物件阵列，每个物件代表一个直播频道。

| 栏位        | 类型              | 说明                                                                                                                 |
|-----------|-----------------|--------------------------------------------------------------------------------------------------------------------|
| `name`    | `string`        | 频道显示名称。                                                                                                            |
| `urls`    | `array<string>` | 频道播放 URL 清单，支援多个备用线路，依序尝试。每条 URL 可附加 `$线路名称` 后缀指定显示名称（如 `"http://cdn1.example.com/hbo.m3u8$CDN1"`）；省略时自动显示为「线路 N」。 |
| `number`  | `string`        | 频道号码（显示用）。                                                                                                         |
| `logo`    | `string`        | 频道 Logo 图片 URL，覆盖来源预设值。                                                                                            |
| `epg`     | `string`        | 此频道专属 EPG URL，覆盖来源预设 EPG。                                                                                          |
| `ua`      | `string`        | 此频道播放请求的 User-Agent，覆盖来源预设值。                                                                                       |
| `click`   | `string`        | 点击拦截处理。                                                                                                            |
| `format`  | `string`        | 指定媒体 MIME type，直接传入播放器。常用值：`"application/x-mpegURL"`（HLS）。                                                         |
| `origin`  | `string`        | 请求 `Origin` 标头值，覆盖来源预设值。                                                                                           |
| `referer` | `string`        | 请求 `Referer` 标头值，覆盖来源预设值。                                                                                          |
| `tvgId`   | `string`        | TVG 格式 EPG 频道 ID。                                                                                                  |
| `tvgName` | `string`        | TVG 格式 EPG 频道名称。                                                                                                   |
| `header`  | `object`        | 此频道请求的额外 HTTP 标头，格式为键值对。                                                                                           |
| `parse`   | `integer`       | 是否需要解析此频道 URL。`0`=不解析，`1`=解析。                                                                                      |
| `catchup` | `Catchup`       | 此频道的追看/时移设定，覆盖分组及来源预设值。详见 [catchup](#catchup--追看时移)。                                                               |
| `drm`     | `Drm`           | DRM 版权保护设定。栏位同 [playerContent 回传的 `drm` 物件](SPIDER.md)。                                                            |

**范例：**

```json
{
  "name": "HBO",
  "urls": [
    "http://cdn1.example.com/hbo.m3u8",
    "http://cdn2.example.com/hbo.m3u8"
  ],
  "number": "601",
  "logo": "https://example.com/logo/hbo.png",
  "ua": "Mozilla/5.0",
  "header": {
    "Referer": "https://www.example.com/"
  },
  "catchup": {
    "type": "append",
    "source": "?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}"
  }
}
```

---

## 共用栏位物件

以下物件可在 Vod 配置或 Live 配置的对应阵列栏位中使用。

> `doh` 仅 Vod 配置支援，其余栏位两者均可使用。

---

### doh — DNS over HTTPS

设定加密 DNS 解析伺服器，保护 DNS 查询隐私并防止污染。仅 Vod 配置支援。

| 栏位     | 类型              | 说明                                                    |
|--------|-----------------|-------------------------------------------------------|
| `name` | `string`        | 伺服器显示名称。                                              |
| `url`  | `string`        | DoH 查询端点 URL（如 `https://dns.google/dns-query`）。       |
| `ips`  | `array<string>` | 伺服器本身的 IP 位址清单，用于 Bootstrap 解析，避免 DoH 伺服器本身需要 DNS 查询。 |

**范例：**

```json
{
  "name": "Google DoH",
  "url": "https://dns.google/dns-query",
  "ips": [
    "8.8.4.4",
    "8.8.8.8"
  ]
}
```

---

### proxy — 代理伺服器

设定特定域名流量的代理规则。支援 HTTP、HTTPS、SOCKS4、SOCKS5 代理。

**代理 URL 格式：**

```
scheme://username:password@host:port
```

| 协议     | 范例                                  |
|--------|-------------------------------------|
| HTTP   | `http://127.0.0.1:7890`             |
| HTTPS  | `https://127.0.0.1:7890`            |
| SOCKS4 | `socks4://127.0.0.1:1080`           |
| SOCKS5 | `socks5://127.0.0.1:7891`           |
| 带认证    | `socks5://user:pass@127.0.0.1:7891` |

| 栏位      | 类型              | 说明                              |
|---------|-----------------|---------------------------------|
| `name`  | `string`        | 代理规则显示名称。                       |
| `hosts` | `array<string>` | 适用此代理的主机名称清单，支援正规表示式。靠前的规则优先匹配。 |
| `urls`  | `array<string>` | 代理伺服器 URL 清单（多个时依序尝试）。          |

**范例：**

```json
{
  "proxy": [
    {
      "name": "指定域名代理",
      "hosts": [
        "googlevideo.com",
        "raw.githubusercontent.com"
      ],
      "urls": [
        "http://127.0.0.1:7890"
      ]
    },
    {
      "name": "全域代理",
      "hosts": [
        ".*"
      ],
      "urls": [
        "socks5://127.0.0.1:7891"
      ]
    }
  ]
}
```

---

### rules — 网路拦截规则

设定 WebView 或播放器的网路请求拦截与处理规则。

| 栏位        | 类型              | 说明                                              |
|-----------|-----------------|-------------------------------------------------|
| `name`    | `string`        | 规则显示名称。                                         |
| `hosts`   | `array<string>` | 触发此规则的目标主机名称清单。                                 |
| `regex`   | `array<string>` | 用于撷取播放 URL 的正规表示式清单，符合者视为有效的媒体 URL。             |
| `script`  | `array<string>` | 在 WebView 中执行的 JavaScript 程式码清单，用于自动点击、关闭广告等操作。 |
| `exclude` | `array<string>` | 排除清单，符合此清单的 URL 不触发 `regex` 撷取。                 |

**范例：**

```json
{
  "rules": [
    {
      "hosts": [
        "video.example.com"
      ],
      "regex": [
        "m3u8?token="
      ],
      "exclude": [
        "preview.json"
      ]
    },
    {
      "hosts": [
        "ads.example.com"
      ],
      "script": [
        "document.querySelector('.close-btn').click()"
      ]
    }
  ]
}
```

---

### headers — 注入回应标头

针对特定主机，在 HTTP 回应中注入自订标头（通常用于解除 CORS 限制）。

| 栏位       | 类型       | 说明                            |
|----------|----------|-------------------------------|
| `host`   | `string` | 目标主机名称（不含协议，如 `example.com`）。 |
| `header` | `object` | 要注入的 HTTP 标头物件，格式为键值对。        |

**范例：**

```json
{
  "host": "stream.example.com",
  "header": {
    "Access-Control-Allow-Origin": "*",
    "User-Agent": "okhttp/3.12.13"
  }
}
```

---

### hosts — DNS 解析覆盖

覆盖特定主机名称的 DNS 解析结果，可用于 CDN 调度或绕过封锁。支援万用字元 `*`。

**格式：** `"原始主机名=目标主机名（或 IP）"`

| 栏位     | 类型       | 说明                              |
|--------|----------|---------------------------------|
| （阵列元素） | `string` | 格式为 `"原始主机名=目标主机名"`，支援万用字元 `*`。 |

**范例：**

```json
{
  "hosts": [
    "stream.example.com=1.2.3.4",
    "old.cdn.example.com=new.cdn.example.com",
    "cache.ott.*.itv.cmvideo.cn=base-v4-free-mghy.e.cdn.chinamobile.com"
  ]
}
```

---

### ads — 广告过滤

广告域名黑名单，符合的 HTTP 请求将被直接拦截拒绝。

| 栏位     | 类型       | 说明      |
|--------|----------|---------|
| （阵列元素） | `string` | 要拦截的域名。 |

**范例：**

```json
{
  "ads": [
    "ads.example.com",
    "tracker.example.net"
  ]
}
```

---

### catchup — 追看/时移

设定频道的回看/时移功能，可定义在 `Live`（来源层级）或 `Channel`（频道层级）。

| 栏位        | 类型       | 说明                                                                                                      |
|-----------|----------|---------------------------------------------------------------------------------------------------------|
| `type`    | `string` | 时移类型。`"append"`（预设）：将格式化后的 `source` 附加至原始 URL 末尾；`"default"`：以格式化后的 `source` 完全替换原始 URL。                |
| `regex`   | `string` | 判断此追看设定是否适用于当前 URL 的比对条件（子字串或正规表示式）。未设定时只要 `source` 非空即启用；设定后只有 URL 符合此条件才启用追看。                         |
| `source`  | `string` | 时移 URL 范本，**非空时才启用追看功能**。支援 `{(b)格式}`（开始时间）、`{(e)格式}`（结束时间）、`{utc:}`（开始 Unix 秒）、`{utcend:}`（结束 Unix 秒）。 |
| `replace` | `string` | 逗号分隔的替换对（`原字串,新字串`），在组合时移 URL 前先对原始 URL 执行替换。                                                           |

**范例：**

```json
{
  "type": "append",
  "source": "?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}"
}
```

---

### style — 卡片样式

设定 Vod 来源的内容卡片显示样式。

| 栏位      | 类型       | 说明                                                  |
|---------|----------|-----------------------------------------------------|
| `type`  | `string` | 卡片类型。可选值：`"rect"`（矩形）、`"oval"`（圆形/椭圆）、`"list"`（列表）。 |
| `ratio` | `float`  | 卡片宽高比（宽度 / 高度）。省略时使用预设比例。                           |

**`ratio` 常用值：**

| `ratio` | 比例   | 用途       |
|---------|------|----------|
| `0.75`  | 3:4  | 直式海报（预设） |
| `1`     | 1:1  | 正方形      |
| `1.33`  | 4:3  | 横式缩图     |
| `1.78`  | 16:9 | 宽萤幕缩图    |

**范例：**

直式（海报，3:4）

```json
{
  "style": {
    "type": "rect"
  }
}
```

横式（4:3）

```json
{
  "style": {
    "type": "rect",
    "ratio": 1.33
  }
}
```

正方（1:1）

```json
{
  "style": {
    "type": "rect",
    "ratio": 1
  }
}
```

正圆

```json
{
  "style": {
    "type": "oval"
  }
}
```

---

## danmaku — 弹幕 API

`danmaku` 为 VodConfig 顶层字串栏位，指定弹幕搜寻接口。

### GET 模式（含占位符）

URL 含 `{name}` 或 `{episode}` 时，以 GET 请求发送，占位符在请求前替换：

| 占位符         | 说明   |
|-------------|------|
| `{name}`    | 剧集名称 |
| `{episode}` | 集数名称 |

```
https://example.com/danmaku?name={name}&episode={episode}
```

### POST 模式（不含占位符）

URL 不含任何占位符时，改以 POST 请求发送，`name`/`episode` 作为 form 栏位传入：

```
https://example.com/danmaku
```

POST body：`name=剧集名称&episode=集数名称`

### 回应格式

接口须回传 JSON 阵列，每个元素为一个弹幕来源物件。

### 自动搜寻条件

- 设定中「弹幕载入」已开启（`DanmakuSetting.isLoad()`）
- 设定中「自动载入」已开启（`DanmakuSetting.isAuto()`）
- 弹幕接口不为空（使用者自订优先，否则取此栏位）

> **提示：** 使用者也可在设定页自订弹幕接口，会优先覆盖此处的设定。

---

## 完整范例

### Vod 配置（config.json）

```json
{
  "spider": "./custom_spider.jar",
  "wallpaper": "./wallpaper.jpg",
  "logo": "./logo.jpg",
  "notice": "欢迎使用，本配置仅供测试！",
  "sites": [
    {
      "key": "push_agent",
      "name": "Push",
      "type": 3,
      "api": "csp_Push"
    },
    {
      "key": "my_source",
      "name": "我的影音",
      "type": 3,
      "api": "csp_MySource",
      "ext": "./myext.json",
      "searchable": 1,
      "changeable": 1,
      "timeout": 15,
      "style": {
        "type": "rect",
        "ratio": 1.33
      }
    }
  ],
  "lives": [
    {
      "name": "直播",
      "url": "./live.json",
      "epg": "https://epg.example.com/?ch={name}"
    }
  ],
  "parses": [
    {
      "name": "官方解析",
      "type": 1,
      "url": "https://api.example.com/parse?url="
    }
  ],
  "doh": [
    {
      "name": "Google",
      "url": "https://dns.google/dns-query",
      "ips": [
        "8.8.4.4",
        "8.8.8.8"
      ]
    }
  ],
  "proxy": [
    {
      "name": "指定代理",
      "hosts": [
        "googlevideo.com"
      ],
      "urls": [
        "http://127.0.0.1:7890"
      ]
    }
  ],
  "rules": [
    {
      "hosts": [
        "video.example.com"
      ],
      "regex": [
        "m3u8?token="
      ],
      "exclude": [
        "preview.json"
      ]
    }
  ],
  "headers": [
    {
      "host": "stream.example.com",
      "header": {
        "User-Agent": "okhttp/3.12.13"
      }
    }
  ],
  "hosts": [
    "old.cdn.example.com=new.cdn.example.com"
  ],
  "flags": [
    "qq"
  ],
  "ads": [
    "ads.example.com"
  ]
}
```

### Live 配置（live.json）

```json
{
  "lives": [
    {
      "name": "台湾频道",
      "epg": "https://epg.example.com/api?id={id},https://epg.example.com/xmltv.xml.gz",
      "ua": "Mozilla/5.0",
      "timeZone": "Asia/Taipei",
      "boot": true,
      "groups": [
        {
          "name": "新闻台",
          "channel": [
            {
              "name": "TVBS新闻台",
              "number": "56",
              "urls": [
                "http://cdn1.example.com/tvbs.m3u8"
              ],
              "logo": "https://example.com/logo/tvbs.png"
            },
            {
              "name": "民视新闻",
              "number": "52",
              "urls": [
                "http://cdn1.example.com/ftv.m3u8",
                "http://cdn2.example.com/ftv.m3u8"
              ],
              "catchup": {
                "type": "append",
                "source": "?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}"
              }
            }
          ]
        }
      ]
    }
  ]
}
```
