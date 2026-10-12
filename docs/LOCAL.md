# 本地 HTTP API

应用程式启动后会在本地绑定一个 HTTP 伺服器，埠号从 **9978** 开始依序尝试至 **9998**，取得第一个可用埠。

```
http://127.0.0.1:{port}
```

> 实际埠号依系统可用情况而定，预设起始为 9978。

所有端点支援 GET 与 POST（除特别标注外），参数可放在 Query String 中。回应若无特别说明皆为 `text/plain`，成功回传 `OK`，失败回传 `500` 与错误讯息。

---

## 目录

- [/action — 动作指令](#action--动作指令)
    - [do=control — 播放控制](#docontrol--播放控制)
    - [do=refresh — 刷新指令](#dorefresh--刷新指令)
    - [do=push — 推送播放](#dopush--推送播放)
    - [do=file — 开启档案](#dofile--开启档案)
    - [do=search — 触发搜寻](#dosearch--触发搜寻)
    - [do=setting — 载入配置](#dosetting--载入配置)
    - [do=cast — 投放媒体](#docast--投放媒体)
    - [do=sync — 同步资料](#dosync--同步资料)
- [/cache — 快取操作](#cache--快取操作)
- [/media — 播放状态](#media--播放状态)
- [/file — 本地档案系统](#file--本地档案系统)
- [/upload — 上传档案](#upload--上传档案)
- [/newFolder — 新增资料夹](#newfolder--新增资料夹)
- [/delFolder — 删除资料夹](#delfolder--删除资料夹)
- [/delFile — 删除档案](#delfile--删除档案)
- [/parse — 解析页面](#parse--解析页面)
- [/proxy — 爬虫代理](#proxy--爬虫代理)
- [/device — 装置资讯](#device--装置资讯)
- [端点总览](#端点总览)

---

## /action — 动作指令

透过 `do` 参数分派不同动作。

```
GET/POST http://127.0.0.1:9978/action?do={do}&...
```

---

### do=control — 播放控制

控制目前播放器的播放状态。

```
http://127.0.0.1:9978/action?do=control&type={type}
```

| 参数     | 说明        |
|--------|-----------|
| `type` | 控制指令，见下表。 |

**`type` 可选值：**

| `type`   | 说明       |
|----------|----------|
| `play`   | 播放       |
| `pause`  | 暂停       |
| `stop`   | 停止       |
| `replay` | 重新播放     |
| `prev`   | 上一集      |
| `next`   | 下一集      |
| `repeat` | 切换循环播放模式 |

**范例：**

```
http://127.0.0.1:9978/action?do=control&type=play
http://127.0.0.1:9978/action?do=control&type=pause
http://127.0.0.1:9978/action?do=control&type=next
```

---

### do=danmaku — 发送弹幕

即时发送一条弹幕文字至目前播放器（需播放器支援弹幕）。

```
http://127.0.0.1:9978/action?do=danmaku&text={text}
```

| 参数     | 说明          |
|--------|-------------|
| `text` | 要发送的弹幕文字内容。 |

**范例：**

```
http://127.0.0.1:9978/action?do=danmaku&text=Hello
```

---

### do=refresh — 刷新指令

触发应用程式重新载入指定页面资料，或推送内容至播放器。

```
http://127.0.0.1:9978/action?do=refresh&type={type}&...
```

**`type` 可选值：**

| `type`     | 额外参数   | 说明                                |
|------------|--------|-----------------------------------|
| `live`     | —      | 重新整理直播页面。                         |
| `detail`   | —      | 重新整理影片详情页。                        |
| `player`   | —      | 重新整理播放页面。                         |
| `subtitle` | `path` | 推送字幕至目前播放器，`path` 为字幕档 URL。       |
| `danmaku`  | `path` | 推送弹幕至目前播放器，`path` 为弹幕档 URL。       |
| `vod`      | `json` | 推送 Vod 物件更新，`json` 为 Vod JSON 字串。 |

**范例：**

```
http://127.0.0.1:9978/action?do=refresh&type=detail
http://127.0.0.1:9978/action?do=refresh&type=subtitle&path=http://example.com/sub.srt
http://127.0.0.1:9978/action?do=refresh&type=danmaku&path=http://example.com/danmaku.xml
```

---

### do=push — 推送播放

推送一个 URL 至应用程式进行播放。

```
http://127.0.0.1:9978/action?do=push&url={url}
```

| 参数    | 说明                    |
|-------|-----------------------|
| `url` | 要播放的媒体 URL（需 URL 编码）。 |

**范例：**

```
http://127.0.0.1:9978/action?do=push&url=http%3A%2F%2Fexample.com%2Fvideo.m3u8
```

---

### do=file — 开启档案

指定本地档案路径，依副档名执行对应动作。

```
http://127.0.0.1:9978/action?do=file&path={path}
```

| 参数     | 说明         |
|--------|------------|
| `path` | 本地档案的绝对路径。 |

**依副档名的行为：**

| 副档名                  | 行为            |
|----------------------|---------------|
| `.apk`               | 触发 APK 安装流程。  |
| `.srt` `.ssa` `.ass` | 注入字幕至目前播放器。   |
| 其他                   | 触发设定页面开启对应档案。 |

---

### do=search — 触发搜寻

在应用程式介面触发关键字搜寻。

```
http://127.0.0.1:9978/action?do=search&word={word}
```

| 参数     | 说明     |
|--------|--------|
| `word` | 搜寻关键字。 |

**范例：**

```
http://127.0.0.1:9978/action?do=search&word=%E9%A3%9F%E7%A5%9E
```

---

### do=setting — 载入配置

载入配置内容或指定名称的配置。

```
http://127.0.0.1:9978/action?do=setting&text={text}&name={name}
```

| 参数     | 说明             |
|--------|----------------|
| `text` | 配置内容字串或配置 URL。 |
| `name` | 配置显示名称（选填）。    |

---

### do=cast — 投放媒体

将指定媒体投放至远端装置播放。

```
http://127.0.0.1:9978/action?do=cast&config={config}&device={device}&history={history}
```

| 参数        | 说明                                 |
|-----------|------------------------------------|
| `config`  | Config 物件的 JSON 字串，指定要投放的配置。       |
| `device`  | 目标装置的 Device 物件 JSON 字串（含 IP 等资讯）。 |
| `history` | History 物件的 JSON 字串，包含播放历史。        |

---

### do=sync — 同步资料

在多个装置间同步观看纪录（`history`）或收藏清单（`keep`）。

```
POST http://127.0.0.1:9978/action?do=sync&type={type}&device={device}&force={force}&mode={mode}
```

**Query 参数：**

| 参数       | 说明                                         |
|----------|--------------------------------------------|
| `type`   | 同步类型：`"history"`（观看纪录）或 `"keep"`（收藏）。      |
| `device` | 目标装置的 Device 物件 JSON 字串。                   |
| `force`  | `"true"` = 先删除后合并；其他 = 直接合并。               |
| `mode`   | `"0"` = 双向（发送+接收）；`"1"` = 仅接收；`"2"` = 仅发送。 |
| `config` | （`history` 用）Config 物件 JSON 字串。            |

**POST Body（`application/x-www-form-urlencoded`）：**

| 参数        | 说明                                |
|-----------|-----------------------------------|
| `targets` | History 或 Keep 物件阵列的 JSON 字串。     |
| `configs` | （`keep` 用）Config URL 阵列的 JSON 字串。 |

---

## /cache — 快取操作

存取应用程式的键值快取（基于 SharedPreferences），可用于爬虫在不同请求间共享资料。

```
GET/POST http://127.0.0.1:9978/cache?do={do}&...
```

**Key 计算规则：** `"cache_" + (rule 为空 ? "" : rule + "_") + key`

---

### do=get — 读取快取

```
http://127.0.0.1:9978/cache?do=get&key={key}&rule={rule}
```

| 参数     | 说明                      |
|--------|-------------------------|
| `key`  | 快取键名。                   |
| `rule` | 命名空间前缀，用于隔离不同爬虫的快取（选填）。 |

**回应：** `200 OK`，回传储存的字串值（若不存在则为空字串）。

---

### do=set — 写入快取

```
http://127.0.0.1:9978/cache?do=set&key={key}&value={value}&rule={rule}
```

| 参数      | 说明          |
|---------|-------------|
| `key`   | 快取键名。       |
| `value` | 要储存的字串值。    |
| `rule`  | 命名空间前缀（选填）。 |

---

### do=del — 删除快取

```
http://127.0.0.1:9978/cache?do=del&key={key}&rule={rule}
```

| 参数     | 说明          |
|--------|-------------|
| `key`  | 要删除的快取键名。   |
| `rule` | 命名空间前缀（选填）。 |

---

## /media — 播放状态

取得目前播放器的媒体资讯与播放状态。

```
GET http://127.0.0.1:9978/media
```

**回应格式：** `application/json`

**播放器未启动时：**

```json
{}
```

**播放器启动时：**

```json
{
  "url": "https://cdn.example.com/video.m3u8",
  "state": 3,
  "speed": 1.0,
  "title": "范例电影",
  "artist": "来源名称",
  "artwork": "https://example.com/cover.jpg",
  "duration": 7200000,
  "position": 1234567
}
```

**栏位说明：**

| 栏位         | 类型        | 说明                                                       |
|------------|-----------|----------------------------------------------------------|
| `url`      | `string`  | 目前串流 URL，无则为 `""`。                                       |
| `state`    | `integer` | PlaybackStateCompat 状态码。`1`=缓冲中，`2`=暂停，`3`=播放中，无则为 `-1`。 |
| `speed`    | `float`   | 播放速率（`1.0` = 正常速度），无则为 `-1`。                             |
| `title`    | `string`  | 媒体标题，无则为 `""`。                                           |
| `artist`   | `string`  | 艺术家或来源名称，无则为 `""`。                                       |
| `artwork`  | `string`  | 封面图 URI，无则为 `""`。                                        |
| `duration` | `long`    | 媒体总时长（毫秒），无则为 `-1`。                                      |
| `position` | `long`    | 目前播放位置（毫秒），无则为 `-1`。                                     |

---

## /file — 本地档案系统

浏览或下载应用程式的本地储存空间。

```
GET http://127.0.0.1:9978/file/{path}
```

| 参数     | 说明                      |
|--------|-------------------------|
| `path` | 相对于应用程式根目录的路径。省略时列出根目录。 |

**目录回应（JSON）：**

```json
{
  "parent": "videos",
  "files": [
    {
      "name": "movie.mp4",
      "path": "videos/movie.mp4",
      "time": "2025/03/05 12:00:00",
      "dir": 0
    },
    {
      "name": "subtitles",
      "path": "videos/subtitles",
      "time": "2025/03/05 10:00:00",
      "dir": 1
    }
  ]
}
```

**`files` 阵列栏位说明：**

| 栏位     | 类型        | 说明                               |
|--------|-----------|----------------------------------|
| `name` | `string`  | 档案或目录名称。                         |
| `path` | `string`  | 相对于根目录的路径。                       |
| `time` | `string`  | 最后修改时间，格式 `yyyy/MM/dd HH:mm:ss`。 |
| `dir`  | `integer` | `1` = 目录，`0` = 档案。               |

**`parent` 栏位说明：**

| 值                  | 意义             |
|--------------------|----------------|
| `"."`              | 目前即为根目录（无上一层）。 |
| `""`               | 上一层为根目录。       |
| `"path/to/parent"` | 上一层目录的相对路径。    |

**档案回应：** 直接串流档案内容，支援 Range 请求（`206 Partial Content`）与 ETag 快取（`304 Not Modified`）。

---

## /upload — 上传档案

上传档案至指定目录，`.zip` 档案会自动解压缩。

```
POST http://127.0.0.1:9978/upload?path={path}
Content-Type: multipart/form-data
```

| 参数     | 说明               |
|--------|------------------|
| `path` | 目标目录，相对于应用程式根目录。 |

| 档案类型   | 行为                   |
|--------|----------------------|
| `.zip` | 解压缩至 `path` 目录。      |
| 其他     | 复制至 `path/filename`。 |

---

## /newFolder — 新增资料夹

在指定路径下建立新目录。

```
GET http://127.0.0.1:9978/newFolder?path={path}&name={name}
```

| 参数     | 说明                |
|--------|-------------------|
| `path` | 父目录路径，相对于应用程式根目录。 |
| `name` | 要建立的资料夹名称。        |

---

## /delFolder — 删除资料夹

删除指定目录及其所有内容。

```
GET http://127.0.0.1:9978/delFolder?path={path}
```

| 参数     | 说明                   |
|--------|----------------------|
| `path` | 要删除的目录路径，相对于应用程式根目录。 |

---

## /delFile — 删除档案

删除指定档案。

```
GET http://127.0.0.1:9978/delFile?path={path}
```

| 参数     | 说明                   |
|--------|----------------------|
| `path` | 要删除的档案路径，相对于应用程式根目录。 |

---

## /parse — 解析页面

将解析器脚本与目标 URL 嵌入 HTML 范本后回传，通常供 WebView 内使用。

```
GET http://127.0.0.1:9978/parse?jxs={jxs}&url={url}
```

| 参数    | 说明            |
|-------|---------------|
| `jxs` | 解析器脚本识别码或内容。  |
| `url` | 待解析的媒体页面 URL。 |

**回应格式：** `text/html`，回传渲染后的 `parse.html` 页面。

---

## /proxy — 爬虫代理

将请求转发至爬虫的 `proxy()` 方法处理，供爬虫自订回应（如转发串流、修改标头等）。

```
GET/POST http://127.0.0.1:9978/proxy?...
```

所有 Query String 参数、请求标头与 POST Body 会合并后传入 `BaseLoader.get().proxy(params)`。回应由爬虫 `proxy()` 决定，框架原封不动地转发爬虫回传的串流与标头。

爬虫如何实作 `proxy()` 方法及取得代理 URL，见 [SPIDER.md — 爬虫本地代理 URL](SPIDER.md#爬虫本地代理-url)。

---

## /device — 装置资讯

取得本机装置资讯。

```
GET http://127.0.0.1:9978/device
```

**回应格式：** `text/plain`，内容为装置资讯的 JSON 字串。

**回应栏位：**

| 栏位       | 类型        | 说明                    |
|----------|-----------|-----------------------|
| `uuid`   | `string`  | 装置唯一识别码（Android ID）。  |
| `name`   | `string`  | 装置显示名称。               |
| `ip`     | `string`  | 装置区域网路 IP 位址（含 port）。 |
| `type`   | `integer` | 装置类型（0=手机, 1=电视）。     |
| `serial` | `string`  | 装置序号。                 |
| `eth`    | `string`  | 有线网路 MAC 位址。          |
| `wlan`   | `string`  | 无线网路 MAC 位址。          |
| `time`   | `long`    | 回应时间戳（毫秒）。            |

---

## 端点总览

| 端点                   | 方法       | 主要参数                              | 说明                                            |
|----------------------|----------|-----------------------------------|-----------------------------------------------|
| `/action?do=control` | GET/POST | `type`                            | 播放控制（play/pause/stop/prev/next/repeat/replay） |
| `/action?do=danmaku` | GET/POST | `text`                            | 即时发送一条弹幕至目前播放器                                |
| `/action?do=refresh` | GET/POST | `type`, `path`, `json`            | 刷新页面或推送字幕/弹幕                                  |
| `/action?do=push`    | GET/POST | `url`                             | 推送 URL 播放                                     |
| `/action?do=file`    | GET/POST | `path`                            | 开启本地档案                                        |
| `/action?do=search`  | GET/POST | `word`                            | 触发关键字搜寻                                       |
| `/action?do=setting` | GET/POST | `text`, `name`                    | 载入配置                                          |
| `/action?do=cast`    | GET/POST | `config`, `device`, `history`     | 投放媒体至远端装置                                     |
| `/action?do=sync`    | POST     | `type`, `device`, `force`, `mode` | 多装置资料同步                                       |
| `/cache?do=get`      | GET/POST | `key`, `rule`                     | 读取快取值                                         |
| `/cache?do=set`      | GET/POST | `key`, `value`, `rule`            | 写入快取值                                         |
| `/cache?do=del`      | GET/POST | `key`, `rule`                     | 删除快取值                                         |
| `/media`             | GET      | —                                 | 取得播放状态 JSON                                   |
| `/file/{path}`       | GET      | —                                 | 浏览目录或下载档案（支援 Range）                           |
| `/upload`            | POST     | `path`（multipart）                 | 上传档案（支援 .zip 解压）                              |
| `/newFolder`         | GET      | `path`, `name`                    | 建立资料夹                                         |
| `/delFolder`         | GET      | `path`                            | 删除资料夹                                         |
| `/delFile`           | GET      | `path`                            | 删除档案                                          |
| `/parse`             | GET      | `jxs`, `url`                      | 取得渲染后的解析 HTML 页面                              |
| `/proxy`             | GET/POST | 自订（转发至爬虫）                         | 爬虫代理转发                                        |
| `/device`            | GET      | —                                 | 取得装置资讯                                        |
