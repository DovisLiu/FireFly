# 爬虫 API 规格说明

本文件说明如何实作一个 Spider 爬虫，包含所有方法的参数、回传格式及 JSON 结构定义。

---

## 目录

- [概览](#概览)
- [爬虫类型与载入方式](#爬虫类型与载入方式)
- [Spider 抽象类别](#spider-抽象类别)
    - [init — 初始化](#init--初始化)
    - [homeContent — 首页分类](#homecontent--首页分类)
    - [homeVideoContent — 首页推荐影片](#homevideocontent--首页推荐影片)
    - [categoryContent — 分类列表](#categorycontent--分类列表)
    - [detailContent — 影片详情](#detailcontent--影片详情)
    - [searchContent — 搜寻](#searchcontent--搜寻)
    - [playerContent — 播放解析](#playercontent--播放解析)
    - [liveContent — 直播频道列表](#livecontent--直播频道列表)
    - [proxy — 本地代理](#proxy--本地代理)
    - [action — 自定义动作](#action--自定义动作)
    - [manualVideoCheck / isVideoFormat — 影片格式判断](#manualvideocheck--isvideoformat--影片格式判断)
    - [destroy — 销毁](#destroy--销毁)
- [回传资料结构](#回传资料结构)
    - [Result — 通用回传物件](#result--通用回传物件)
    - [Vod — 影片卡片物件](#vod--影片卡片物件)
    - [Class — 分类物件](#class--分类物件)
    - [Filter — 筛选器物件](#filter--筛选器物件)
    - [Danmaku — 弹幕物件](#danmaku--弹幕物件)
    - [Sub — 字幕物件](#sub--字幕物件)
    - [Drm — DRM 设定物件](#drm--drm-设定物件)
    - [播放集数格式（vod_play_from / vod_play_url）](#播放集数格式vod_play_from--vod_play_url)
- [完整 JSON 范例](#完整-json-范例)
    - [homeContent 回传范例](#homecontent-回传范例)
    - [homeVideoContent / categoryContent 回传范例](#homevideocontent--categorycontent-回传范例)
    - [detailContent 回传范例](#detailcontent-回传范例)
    - [playerContent 回传范例](#playercontent-回传范例)
    - [searchContent 回传范例](#searchcontent-回传范例)
    - [liveContent 回传范例](#livecontent-回传范例)
- [爬虫本地代理 URL](#爬虫本地代理-url)

---

## 概览

Spider 是应用程式爬虫的抽象基底类别，位于 `com.github.catvod.crawler.Spider`。每个影片来源（`Site`）对应一个 Spider 实例。

**生命周期：**

```
init(context, ext)
    │
    ├─► homeContent(filter)          首页分类
    ├─► homeVideoContent()           首页推荐
    ├─► categoryContent(...)         分类浏览
    ├─► detailContent(ids)           影片详情
    ├─► searchContent(key, quick)    搜寻
    ├─► playerContent(flag, id, ...) 播放解析
    ├─► liveContent(url)             直播解析
    └─► destroy()                    清理资源
```

**栏位：**

| 栏位        | 类型       | 说明                             |
|-----------|----------|--------------------------------|
| `siteKey` | `String` | 由载入器注入，标识此 Spider 服务的来源 `key`。 |

---

## 爬虫类型与载入方式

在 `sites` 配置中，`type` 栏位决定呼叫方式，`api` 栏位决定载入哪种引擎：

| `type` | `api` 格式        | 引擎                  | 说明                                                         |
|--------|-----------------|---------------------|------------------------------------------------------------|
| `0`    | HTTP URL        | 内建 XML 解析           | 直接 GET 请求，回传 XML 格式。                                       |
| `1`    | HTTP URL        | 内建 JSON+Filter      | 直接 GET 请求，回传 JSON 格式，筛选参数以 `f=` 传递。                        |
| `3`    | `csp_ClassName` | JAR（DexClassLoader） | 从 `jar` 指定的 .jar 档载入 `com.github.catvod.spider.ClassName`。 |
| `3`    | `xxx.js`        | JavaScript（QuickJS） | 载入 `.js` 档作为 Spider。                                       |
| `3`    | `xxx.py`        | Python（Chaquopy）    | 载入 `.py` 档作为 Spider。                                       |
| `4`    | HTTP URL        | 内建 JSON+Base64 ext  | 同 `1`，扩充参数以 Base64 编码传递（`ext=`）。                           |

> 本文件主要说明 `type=3`（Spider 直接呼叫）的情境。

---

## Spider 抽象类别

所有方法预设回传空字串 `""`，子类别仅需覆写所需功能。

---

### init — 初始化

```java
public void init(Context context, String extend) throws Exception
```

**触发时机：** Spider 实例建立后呼叫一次，用于初始化连线、载入设定等。

| 参数        | 类型        | 说明                                                    |
|-----------|-----------|-------------------------------------------------------|
| `context` | `Context` | Android Context，可取得应用资源、路径等。                          |
| `extend`  | `String`  | 对应 `Site.ext` 栏位的额外扩充资料，内容由爬虫自行定义（可为 URL、JSON 字串或路径）。 |

**回传：** 无（`void`）

---

### homeContent — 首页分类

```java
public String homeContent(boolean filter) throws Exception
```

**触发时机：** 使用者进入首页时呼叫，取得分类列表（及可选的筛选器）。

| 参数       | 类型        | 说明                                |
|----------|-----------|-----------------------------------|
| `filter` | `boolean` | `true` 表示需要回传筛选器资料（`filters` 栏位）。 |

**回传：** JSON 字串，结构为 [Result](#result--通用回传物件)。

`class`（分类列表）为主要回传栏位，`filters`（各分类的筛选器定义）为选填。

---

### homeVideoContent — 首页推荐影片

```java
public String homeVideoContent() throws Exception
```

**触发时机：** 首页分类载入完成后呼叫，取得首页推荐影片列表。

**回传：** JSON 字串，结构为 [Result](#result--通用回传物件)。

主要回传栏位为 `list`（推荐影片列表）。

---

### categoryContent — 分类列表

```java
public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception
```

**触发时机：** 使用者点击分类或切换筛选条件时呼叫。

| 参数       | 类型                        | 说明                                                   |
|----------|---------------------------|------------------------------------------------------|
| `tid`    | `String`                  | 分类 ID，对应 `Class.typeId`。                             |
| `pg`     | `String`                  | 页码，从 `"1"` 开始。                                       |
| `filter` | `boolean`                 | 是否启用筛选器。                                             |
| `extend` | `HashMap<String, String>` | 使用者选择的筛选条件，key 为筛选器 ID，value 为选项 key。为空 `{}` 时表示无筛选。 |

**回传：** JSON 字串，结构为 [Result](#result--通用回传物件)。

主要回传栏位为 `list`（影片列表），选填 `pagecount`（总页数，用于分页控制）。

---

### detailContent — 影片详情

```java
public String detailContent(List<String> ids) throws Exception
```

**触发时机：** 使用者点击影片卡片时呼叫，取得完整详情与播放集数。

| 参数    | 类型             | 说明                                |
|-------|----------------|-----------------------------------|
| `ids` | `List<String>` | 影片 ID 清单，通常只含一个元素，对应 `Vod.vodId`。 |

**回传：** JSON 字串，结构为 [Result](#result--通用回传物件)，`list` 阵列中有一个完整的 `Vod` 物件。

主要 Vod 栏位：`vod_id`、`vod_name`、`vod_play_from`、`vod_play_url`。

---

### searchContent — 搜寻

```java
public String searchContent(String key, boolean quick) throws Exception

public String searchContent(String key, boolean quick, String pg) throws Exception
```

**触发时机：** 使用者输入关键字搜寻时呼叫。

| 参数      | 类型        | 说明                                     |
|---------|-----------|----------------------------------------|
| `key`   | `String`  | 搜寻关键字。框架会自动进行繁→简转换以提升相容性。              |
| `quick` | `boolean` | `true` 表示快速搜寻（只传回基本资讯），`false` 表示完整搜寻。 |
| `pg`    | `String`  | 页码（仅分页版本），从 `"1"` 开始。                  |

**回传：** JSON 字串，结构为 [Result](#result--通用回传物件)。

主要回传栏位为 `list`（搜寻结果影片列表）。

> 若 `Site.quickSearch = 0`，快速搜寻会被跳过，直接回传空结果。

---

### playerContent — 播放解析

```java
public String playerContent(String flag, String id, List<String> vipFlags) throws Exception
```

**触发时机：** 使用者选择集数准备播放时呼叫，需解析出实际的媒体 URL。

| 参数         | 类型             | 说明                                                     |
|------------|----------------|--------------------------------------------------------|
| `flag`     | `String`       | 播放来源名称，对应 `vod_play_from` 中的一项（如 `"youku"`、`"iqiyi"`）。 |
| `id`       | `String`       | 集数 URL 或 ID，对应 `vod_play_url` 中某集数的 value 部分。          |
| `vipFlags` | `List<String>` | 全局 VIP 平台旗标清单，对应配置中的 `flags` 栏位（如 `["qq", "youku"]`）。  |

**回传：** JSON 字串，结构为 [Result](#result--通用回传物件)（播放解析结果）。

主要回传栏位为 `url`（实际可播放的媒体 URL）。

选填栏位：

| 栏位         | 说明                                                                  |
|------------|---------------------------------------------------------------------|
| `parse`    | `0` = 直接播放，`1` = 需进一步解析（预设 `0`）。`jx=1` 效果相同。                        |
| `jx`       | 同 `parse=1`，需进一步解析。                                                 |
| `playUrl`  | 解析器前缀或指定。`json:…` 传入 JSON 解析器，`parse:解析器名称` 指定具名解析器，其他值作为解析 URL 前缀。 | |
| `click`    | 点击拦截处理 URL，传递给解析器 WebView。                                          |
| `code`     | 非零时抑制 `msg` 显示。                                                     |
| `header`   | 播放请求所需的 HTTP 标头（键值对）。                                               |
| `flag`     | 覆盖来源旗标，传入 VIP 解析器时使用。                                               |
| `jxFrom`   | 强制指定解析器旗标（覆盖 `flag` 的解析器比对结果）。                                      |
| `format`   | 媒体 MIME type（如 `"application/x-mpegURL"`），指定后播放器跳过格式自动侦测。           |
| `danmaku`  | 弹幕资料列表，详见 [Danmaku](#danmaku--弹幕物件)。                                |
| `subs`     | 字幕列表，详见 [Sub](#sub--字幕物件)。                                          |
| `drm`      | DRM 版权保护设定，详见 [Drm](#drm--drm-设定物件)。                                |
| `artwork`  | 播放页面封面图 URL。                                                        |
| `desc`     | 播放页面描述文字。                                                           |
| `position` | 播放恢复位置（毫秒）。                                                         |

---

### liveContent — 直播频道列表

```java
public String liveContent(String url) throws Exception
```

**触发时机：** 载入直播来源时呼叫，爬虫回传频道列表的原始文字，框架再依格式解析（支援 TXT、M3U、JSON）。

| 参数    | 类型       | 说明                     |
|-------|----------|------------------------|
| `url` | `String` | 来源配置中的 `Live.url` 栏位值。 |

**回传：** 频道列表的原始文字字串（非 JSON Result），格式可为：

| 格式   | 说明                                    |
|------|---------------------------------------|
| TXT  | 每行 `频道名称,URL#URL2...`，以 `#genre#` 分组。 |
| M3U  | 标准 `#EXTM3U`/`#EXTINF` 格式。            |
| JSON | `Group` 物件阵列，结构与配置的 `groups` 栏位相同。    |

---

### proxy — 本地代理

```java
public Object[] proxy(Map<String, String> params) throws Exception
```

**触发时机：** 应用程式内建本地 HTTP 代理伺服器收到请求时呼叫。

| 参数       | 类型                    | 说明                                                           |
|----------|-----------------------|--------------------------------------------------------------|
| `params` | `Map<String, String>` | 代理请求参数，从本地代理 URL 的 query string 解析而来。通常含有 `do`、`url` 等自定义参数。 |

**回传：** `Object[]`（注意：非 JSON 字串），格式为：

```
// 200 正常回应
Object[] {
  Integer    statusCode,   // HTTP 状态码（200）
  String     mimeType,     // Content-Type（如 "video/mp2t"）
  InputStream body         // 回应内容
}

// 302 重定向
Object[] {
  Integer              statusCode,   // 302
  String               mimeType,     // "text/plain"
  InputStream          body,         // 通常为空或提示文字
  Map<String, String>  headers       // 含 "Location" key 的重定向标头
}
```

---

### action — 自定义动作

```java
public String action(String action) throws Exception
```

**触发时机：** UI 层呼叫特定自定义指令时呼叫（如登入、重新整理 Token 等）。`action` 字串格式由爬虫自行定义，框架不解析其内容。

| 参数       | 类型       | 说明                |
|----------|----------|-------------------|
| `action` | `String` | 动作指令字串，格式由爬虫自行定义。 |

**回传：** JSON 字串，结构为 [Result](#result--通用回传物件)。

---

### manualVideoCheck / isVideoFormat — 影片格式判断

```java
public boolean manualVideoCheck() throws Exception

public boolean isVideoFormat(String url) throws Exception
```

| 方法                   | 说明                                                             |
|----------------------|----------------------------------------------------------------|
| `manualVideoCheck()` | 回传 `true` 时，框架在 WebView 中拦截 URL 后会呼叫 `isVideoFormat()` 进行人工判断。 |
| `isVideoFormat(url)` | 判断指定 URL 是否为有效的直接媒体 URL。回传 `true` 表示可直接播放。                     |

| 参数（`isVideoFormat`） | 类型       | 说明        |
|---------------------|----------|-----------|
| `url`               | `String` | 待判断的 URL。 |

---

### destroy — 销毁

```java
public void destroy()
```

**触发时机：** 配置重新载入或应用程式清理快取时呼叫，释放资源（连线、执行绪等）。

**回传：** 无（`void`）

---

## 回传资料结构

所有方法（`proxy` 除外）的回传值均为 JSON 字串，解析后对应以下物件。

---

### Result — 通用回传物件

不同方法使用的栏位不同，以下按方法分组说明。

**homeContent：**

| JSON 栏位   | 类型             | 说明                                                                     |
|-----------|----------------|------------------------------------------------------------------------|
| `class`   | `array<Class>` | 分类列表。详见 [Class](#class--分类物件)。                                         |
| `filters` | `object`       | 筛选器定义，key 为 `type_id`，value 为 `Filter` 阵列。详见 [Filter](#filter--筛选器物件)。 |

**homeVideoContent / categoryContent / detailContent / searchContent：**

| JSON 栏位     | 类型           | 说明                                         |
|-------------|--------------|--------------------------------------------|
| `list`      | `array<Vod>` | 影片卡片列表。详见 [Vod](#vod--影片卡片物件)。             |
| `pagecount` | `integer`    | 总页数（`categoryContent`、`searchContent` 使用）。 |

**playerContent：**

| JSON 栏位    | 类型               | 说明                                                                                 |
|------------|------------------|------------------------------------------------------------------------------------|
| `url`      | `string`         | 实际播放媒体 URL。                                                                        |
| `parse`    | `integer`        | `0` = 直接播放，`1` = 需进一步解析（预设 `0`）。`jx=1` 效果相同。                                       |
| `jx`       | `integer`        | 同 `parse=1`，需进一步解析（两者任一为 `1` 即触发解析流程）。                                             |
| `playUrl`  | `string`         | 解析器前缀或指定。`json:…` 传入 JSON 解析器，`parse:解析器名称` 指定具名解析器，其他值作为解析 URL 前缀。                |
| `key`      | `string`         | 来源 `key`，用于从配置查找对应 `Site.click`。当爬虫未回传 `click` 时，框架以此 key 从 VodConfig 取得 click。    |
| `click`    | `string`         | 点击拦截处理 URL，传递给解析器 WebView 执行点击动作。                                                  |
| `code`     | `integer`        | 非零时抑制 `msg` 显示（通常用于错误状态码）。                                                         |
| `header`   | `object`         | 播放请求的额外 HTTP 标头，键值对格式。                                                             |
| `flag`     | `string`         | 播放来源旗标名称，覆盖原始 `flag` 参数。                                                           |
| `jxFrom`   | `string`         | 强制指定解析器旗标（覆盖 `flag` 的解析器比对结果）。                                                     |
| `format`   | `string`         | 媒体 MIME type（如 `"application/x-mpegURL"`、`"application/dash+xml"`），指定后播放器跳过格式自动侦测。 |
| `danmaku`  | `array<Danmaku>` | 弹幕资料列表，详见 [Danmaku](#danmaku--弹幕物件)。                                               |
| `subs`     | `array<Sub>`     | 字幕列表，详见 [Sub](#sub--字幕物件)。                                                         |
| `drm`      | `Drm`            | DRM 版权保护设定，详见 [Drm](#drm--drm-设定物件)。                                               |
| `artwork`  | `string`         | 播放页面封面图 URL。                                                                       |
| `desc`     | `string`         | 播放页面描述文字。                                                                          |
| `position` | `long`           | 播放恢复位置（毫秒）。                                                                        |
| `lrc`      | `string`         | 歌词 URL（音乐类来源使用）。                                                                   |

**通用栏位（所有 JSON 回传方法）：**

| JSON 栏位 | 类型       | 说明       |
|---------|----------|----------|
| `msg`   | `string` | 错误或提示讯息。 |

---

### Vod — 影片卡片物件

`list` 阵列中的每个元素。

| JSON 栏位         | 类型        | 说明                                                                                            |
|-----------------|-----------|-----------------------------------------------------------------------------------------------|
| `vod_id`        | `string`  | **影片唯一 ID**，传入 `detailContent` 的 `ids` 参数。                                                    |
| `vod_name`      | `string`  | 影片显示名称（支援 HTML 编码）。                                                                           |
| `vod_pic`       | `string`  | 缩图 URL。                                                                                       |
| `vod_remarks`   | `string`  | 备注标签，显示在缩图上（如 `"更新至12集"`、`"HD"`）。                                                             |
| `type_name`     | `string`  | 所属分类名称（用于分类过滤）。                                                                               |
| `vod_year`      | `string`  | 年份。                                                                                           |
| `vod_area`      | `string`  | 地区。                                                                                           |
| `vod_director`  | `string`  | 导演。                                                                                           |
| `vod_actor`     | `string`  | 演员。                                                                                           |
| `vod_content`   | `string`  | 简介/描述。                                                                                        |
| `vod_play_from` | `string`  | 播放来源名称，多个来源以 `$$$` 分隔。                                                                        |
| `vod_play_url`  | `string`  | 播放集数 URL，格式详见[下方说明](#播放集数格式vod_play_from--vod_play_url)。                                      |
| `vod_tag`       | `string`  | 特殊标记。`"folder"` 表示此项为资料夹，点击后以 `vod_id` 作为 `tid` 呼叫 `categoryContent` 取得子列表。                   |
| `action`        | `string`  | 自订动作字串，点击时优先于资料夹行为触发。type==3 呼叫 `Spider.action(action)`，type==4 发送 HTTP 请求，结果以 Toast 显示。      |
| `cate`          | `Cate`    | 资料夹显示样式物件，包含 `land`、`circle`、`ratio` 三个子栏位（含义同下方三栏）。设定此栏位等同于 `vod_tag: "folder"`，即自动将此项视为资料夹。 |
| `land`          | `integer` | 横向显示旗标，覆盖 [Class](#class--分类物件) 层级的 `land` 设定。                                                |
| `circle`        | `integer` | 圆形显示旗标，覆盖 [Class](#class--分类物件) 层级的 `circle` 设定。                                              |
| `ratio`         | `float`   | 卡片宽高比，覆盖 [Class](#class--分类物件) 层级的 `ratio` 设定。                                                |
| `style`         | `Style`   | 此影片卡片的显示样式覆盖，详见 [CONFIG.md](CONFIG.md)。                                                       |

---

### Class — 分类物件

`class` 阵列中的每个元素。

| JSON 栏位     | 类型        | 说明                                                 |
|-------------|-----------|----------------------------------------------------|
| `type_id`   | `string`  | 分类唯一 ID，传入 `categoryContent` 的 `tid` 参数。可缩写为 `id`。 |
| `type_name` | `string`  | 分类显示名称。可缩写为 `name`。                                |
| `type_flag` | `string`  | `"1"` 表示此分类为资料夹类型。                                 |
| `land`      | `integer` | 此分类下影片的横向显示旗标。                                     |
| `circle`    | `integer` | 此分类下影片的圆形显示旗标。                                     |
| `ratio`     | `float`   | 此分类下影片卡片的宽高比。                                      |

---

### Filter — 筛选器物件

`filters` 为一个物件，key 为 `type_id`，value 为 `Filter` 阵列，每个 `Filter` 定义一个筛选维度。

```json
{
  "filters": {
    "1": [
      {
        "key": "area",
        "name": "地区",
        "value": [
          {"n": "全部", "v": ""},
          {"n": "大陆", "v": "大陆"},
          {"n": "美国", "v": "美国"}
        ]
      },
      {
        "key": "year",
        "name": "年份",
        "value": [
          {"n": "全部", "v": ""},
          {"n": "2024", "v": "2024"}
        ]
      }
    ]
  }
}
```

**Filter 栏位：**

| JSON 栏位 | 类型       | 说明                                              |
|---------|----------|-------------------------------------------------|
| `key`   | `string` | 筛选器 ID，作为 `categoryContent` 的 `extend` 参数的 key。 |
| `name`  | `string` | 筛选器显示名称。                                        |
| `init`  | `string` | 预设选中的选项 value（选填）。                              |
| `value` | `array`  | 可选项目列表，每项含 `n`（显示名称）与 `v`（传入值）。                 |

使用者选择后，`extend` 传入格式为：

```json
{
  "area": "大陆",
  "year": "2024"
}
```

---

### Danmaku — 弹幕物件

`danmaku` 阵列中的每个元素。

| JSON 栏位 | 类型       | 说明                           |
|---------|----------|------------------------------|
| `url`   | `string` | 弹幕来源 URL（必填），支援本地路径（`/` 开头）。 |
| `name`  | `string` | 显示名称（选填），省略时使用 `url`。        |

---

### Sub — 字幕物件

`subs` 阵列中的每个元素。

| JSON 栏位  | 类型        | 说明                                                                                                                         |
|----------|-----------|----------------------------------------------------------------------------------------------------------------------------|
| `url`    | `string`  | 字幕档 URL（必填）。                                                                                                               |
| `name`   | `string`  | 显示名称（选填）。                                                                                                                  |
| `lang`   | `string`  | 语言代码（选填，如 `"zh-tw"`、`"en"`）。                                                                                               |
| `format` | `string`  | MIME 类型（选填），常用值：`"text/x-ssa"`、`"application/x-subrip"`。省略时框架依副档名自动侦测。                                                     |
| `flag`   | `integer` | ExoPlayer `C.SELECTION_FLAG_*` 常数（选填）。`0` 或省略时预设为 `SELECTION_FLAG_DEFAULT`（自动选择）；`2` = `SELECTION_FLAG_FORCED`（强制显示，不可关闭）。 |

---

### Drm — DRM 设定物件

`drm` 物件栏位。

| JSON 栏位    | 类型        | 说明                                                     |
|------------|-----------|--------------------------------------------------------|
| `type`     | `string`  | DRM 类型：`"widevine"`、`"playready"`、`"clearkey"`。        |
| `key`      | `string`  | License Server URL（Widevine/PlayReady）或 ClearKey 金钥字串。 |
| `header`   | `object`  | License 请求的额外 HTTP 标头，键值对格式（选填）。                       |
| `forceKey` | `boolean` | `true` = 强制使用预设 License URI（选填，预设 `false`）。            |

---

### 播放集数格式（vod_play_from / vod_play_url）

`detailContent` 回传的 `Vod` 物件中，集数资讯以特定分隔符号编码在两个字串栏位中。

| 符号    | 用途              |
|-------|-----------------|
| `$$$` | 分隔多个播放来源（group） |
| `#`   | 分隔同一来源下的集数      |
| `$`   | 分隔集数名称与集数 URL   |

**范例：**

```
vod_play_from: "线路一$$$线路二"

vod_play_url:  "第01集$https://cdn1.example.com/ep1.m3u8#第02集$https://cdn1.example.com/ep2.m3u8$$$第01集$https://cdn2.example.com/ep1.m3u8#第02集$https://cdn2.example.com/ep2.m3u8"
```

对应解析结果：

```
线路一:
  - 第01集 → https://cdn1.example.com/ep1.m3u8
  - 第02集 → https://cdn1.example.com/ep2.m3u8

线路二:
  - 第01集 → https://cdn2.example.com/ep1.m3u8
  - 第02集 → https://cdn2.example.com/ep2.m3u8
```

集数 URL 的 value 部分即为 `playerContent` 的 `id` 参数。

---

## 完整 JSON 范例

### homeContent 回传范例

```json
{
  "class": [
    {
      "type_id": "1",
      "type_name": "电影"
    },
    {
      "type_id": "2",
      "type_name": "电视剧"
    },
    {
      "type_id": "3",
      "type_name": "综艺"
    },
    {
      "type_id": "4",
      "type_name": "动漫"
    }
  ],
  "filters": {
    "1": [
      {
        "key": "area",
        "name": "地区",
        "value": [
          {"n": "全部", "v": ""},
          {"n": "大陆", "v": "大陆"},
          {"n": "香港", "v": "香港"},
          {"n": "台湾", "v": "台湾"},
          {"n": "美国", "v": "美国"}
        ]
      },
      {
        "key": "year",
        "name": "年份",
        "value": [
          {"n": "全部", "v": ""},
          {"n": "2025", "v": "2025"},
          {"n": "2024", "v": "2024"}
        ]
      }
    ]
  }
}
```

---

### homeVideoContent / categoryContent 回传范例

> `categoryContent` 和 `searchContent` 可额外回传 `pagecount`；`homeVideoContent` 无此栏位。

```json
{
  "list": [
    {
      "vod_id": "12345",
      "vod_name": "范例电影",
      "vod_pic": "https://example.com/pic/12345.jpg",
      "vod_remarks": "HD",
      "type_name": "电影"
    },
    {
      "vod_id": "67890",
      "vod_name": "范例电视剧",
      "vod_pic": "https://example.com/pic/67890.jpg",
      "vod_remarks": "更新至12集",
      "type_name": "电视剧"
    }
  ],
  "pagecount": 10
}
```

---

### detailContent 回传范例

```json
{
  "list": [
    {
      "vod_id": "12345",
      "vod_name": "范例电影",
      "vod_pic": "https://example.com/pic/12345.jpg",
      "vod_year": "2024",
      "vod_area": "大陆",
      "vod_director": "张三",
      "vod_actor": "李四, 王五",
      "vod_content": "这是一部精彩的电影...",
      "vod_remarks": "HD",
      "type_name": "电影",
      "vod_play_from": "线路一$$$线路二",
      "vod_play_url": "正片$https://cdn1.example.com/movie.m3u8$$$正片$https://cdn2.example.com/movie.m3u8"
    }
  ]
}
```

**多集电视剧范例：**

```json
{
  "list": [
    {
      "vod_id": "67890",
      "vod_name": "范例电视剧",
      "vod_play_from": "主线路$$$备用线路",
      "vod_play_url": "第01集$https://cdn1.example.com/ep1.m3u8#第02集$https://cdn1.example.com/ep2.m3u8$$$第01集$https://cdn2.example.com/ep1.m3u8#第02集$https://cdn2.example.com/ep2.m3u8"
    }
  ]
}
```

---

### playerContent 回传范例

**直接播放（无需解析）：**

```json
{
  "parse": 0,
  "url": "https://cdn.example.com/video/ep1.m3u8",
  "header": {
    "User-Agent": "Mozilla/5.0",
    "Referer": "https://www.example.com/"
  }
}
```

**需要进一步解析（VIP 影片）：**

```json
{
  "parse": 1,
  "url": "https://www.youku.com/video/id_xxx.html",
  "flag": "youku"
}
```

**含字幕与弹幕：**

```json
{
  "parse": 0,
  "url": "https://cdn.example.com/video/ep1.m3u8",
  "header": {
    "Referer": "https://www.example.com/"
  },
  "subs": [
    {
      "name": "繁体中文",
      "url": "https://cdn.example.com/sub/ep1.zh-tw.srt",
      "lang": "zh-tw"
    },
    {
      "name": "英文",
      "url": "https://cdn.example.com/sub/ep1.en.srt",
      "lang": "en"
    }
  ],
  "danmaku": [
    {
      "url": "https://danmaku.example.com/ep1.xml"
    }
  ]
}
```

---

### searchContent 回传范例

```json
{
  "list": [
    {
      "vod_id": "12345",
      "vod_name": "范例电影",
      "vod_pic": "https://example.com/pic/12345.jpg",
      "vod_remarks": "HD",
      "type_name": "电影"
    }
  ]
}
```

---

### liveContent 回传范例

**TXT 格式：**

```
央视频道,#genre#
CCTV1,http://example.com/cctv1.m3u8#http://cdn2.example.com/cctv1.m3u8
CCTV2,http://example.com/cctv2.m3u8
台湾频道,#genre#
TVBS,http://example.com/tvbs.m3u8
```

**M3U 格式：**

```
#EXTM3U
#EXTINF:-1 tvg-name="CCTV1" group-title="央视频道",CCTV1
http://example.com/cctv1.m3u8
```

**JSON 格式：**

```json
[
  {
    "name": "央视频道",
    "channel": [
      {
        "name": "CCTV1",
        "urls": [
          "http://example.com/cctv1.m3u8"
        ]
      }
    ]
  }
]
```

---

## 爬虫本地代理 URL

爬虫可在回传的媒体 URL 中使用 `proxy://` 协议，将请求导向本地代理伺服器，由对应语言的 `proxy()`
方法处理。这样可以在播放器无法直接存取来源时，让爬虫居中转发资料。

| 语言         | 回传 URL 前缀        | 取得代理 URL 的方法                  |
|------------|------------------|-------------------------------|
| Java（JAR）  | `proxy://`       | `Proxy.getUrl(boolean local)` |
| Python     | `proxy://?do=py` | `getProxyUrl(boolean local)`  |
| JavaScript | `proxy://?do=js` | `getProxy(boolean local)`     |

> `local` 参数：`true` 取得本地（`127.0.0.1`）代理位址，`false` 取得可对外存取的 LAN IP 位址。

完整端点说明见 [LOCAL.md — /proxy](LOCAL.md#proxy--爬虫代理)。

**使用范例（Java）：**

```java
@Override
public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
    String proxyUrl = Proxy.getUrl(true) + "?url=" + URLEncoder.encode(id, "UTF-8") + "&token=xxx";
    return "{\"parse\":0,\"url\":\"" + proxyUrl + "\"}";
}

@Override
public Object[] proxy(Map<String, String> params) throws Exception {
    String url = params.get("url");
    String token = params.get("token");
    InputStream stream = fetchWithAuth(url, token);
    return new Object[]{200, "video/mp2t", stream};
}
```
