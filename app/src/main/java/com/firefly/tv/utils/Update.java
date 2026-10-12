package com.firefly.tv.utils;

public class Update {

    // 自有下载站（EdgeOne 加速，回源轻量服务器静态目录），更新链路第一优先。
    // APK 文件名自带版本号（如 FireFly.TV.1.0.0.apk），平铺在 firefly/ 根：
    // 版本号在文件名里 → URL 唯一，EO immutable 缓存不串版本，无需按版本建目录；
    // JSON 同样平铺（源站 no-cache），保证版本检测即时生效。
    private static final String SERVER = "https://download.dovisliu.cn/firefly";

    // 版本检测顺序：服务器（→ GitHub Release 仓 FireFly 分支镜像） → Gitee → GitHub，配合 Updater.fetchJson 的"有新版即停"短路。
    // JSON 按端命名：tv.json / phone.json（对应 leanback / mobile 两个 flavor）。
    private static String jsonName(String mode) {
        return "leanback".equals(mode) ? "tv" : "phone";
    }

    // Release 仓 FireFly 分支根目录镜像（JSON 与 APK 同级，无 apk/ 子目录，无同步脚本）
    private static String repoJson(String file) {
        return "https://raw.githubusercontent.com/DovisLiu/Release/FireFly/" + file;
    }

    public static String[] jsonUrls(String mode) {
        String json = jsonName(mode) + ".json";
        return new String[]{
            SERVER + "/" + json,
            repoJson(json)
        };
    }

    // 测试通道（仅手动触发）：JSON 与 APK 同在服务器 firefly/ 根，命名 tv.beta.json / phone.beta.json。
    // 只在"连点版本行 5 次"的手动检查里读取，自动更新永不读取；刻意不放镜像，避免测试包外流。
    // 版本号可人为指定（如 1.0.2-beta1），但 code 必须介于当前正式版与下一个正式版之间，
    // 否则测试版装上后正式版无法覆盖（例如正式 1.0.1=101，beta=102，正式 1.0.2=103）。
    public static String betaJsonUrl(String mode) {
        return SERVER + "/" + jsonName(mode) + ".beta.json";
    }

    // 下载顺序：服务器（带版本号文件名）→ GitHub Release 仓 FireFly 分支→ GitHub（Gitee 匿名拉大文件 403，不参与下载）
    public static String[] getApkUrls(String version, String mode, String abi) {
        String file = apkName(version, mode, abi) + ".apk";
        return new String[]{
            SERVER + "/" + file,
            repoJson(file)
        };
    }

    // 与 app/build.gradle 的 androidComponents 输出命名保持一致（改一处必须同步另一处）：
    // FireFly.TV.1.0.0.apk / FireFly.TV.arm64.1.0.0.apk / FireFly.Phone.1.0.0.apk。
    // 命名规则：手机版永远不带 ABI 后缀；电视版合版不带后缀，分包标 .arm64 / .arm32
    private static String apkName(String version, String mode, String abi) {
        String app = "leanback".equals(mode) ? "FireFly.TV" : "FireFly.Phone";
        String part = ("leanback".equals(mode) && !"universal".equals(abi))
            ? ("arm64_v8a".equals(abi) ? ".arm64" : ".arm32") : "";
        return app + part + "." + version;
    }

    // 远程公告通道：自有服务器 → Gitee → GitHub（与版本检测同序）。
    // 文件名刻意改后缀（notice.dat）：不是 .json，不暴露公告/锁定的用途，避免被按图索骥；内容格式仍为 JSON。
    // 服务器文件在 firefly/ 根（no-cache）；镜像放 apk/notice.dat。
    // 注意：公告管道地址属于流萤自身，目标应用的下载地址由其内容下发（留空间），互不耦合。
    // 公告/停服指令只走自有服务器，内容保密，不做任何镜像（2026-10-09）
    public static String[] noticeUrls() {
        return new String[]{SERVER + "/notice.dat"};
    }
}
