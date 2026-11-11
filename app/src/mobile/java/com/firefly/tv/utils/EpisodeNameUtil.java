package com.firefly.tv.utils;

import java.util.regex.Pattern;

/**
 * β31 A4：选集显示名清洗（mobile 共享，TV 侧不连带）。
 * 去掉源返回名里的 [2.63GB]/[x.xxMB] 大小前缀与 .mp3/.mp4/.mkv 等扩展名后缀，保留名主体。
 * 供 EpisodeHoriHolder（横向选集卡）与 EpisodeGridHolder（共N集弹层宫格）共用。
 * 只剥大小前缀与扩展名两步；不猜集号、不动 Episode.name 字段。剥完为空时返回空串，由调用方回落原名。
 */
public final class EpisodeNameUtil {

    private static final Pattern SIZE = Pattern.compile("^\\[\\d+(\\.\\d+)?(GB|MB)\\]\\s*");
    private static final Pattern EXT = Pattern.compile("(?i)\\.(mkv|mp4|mp3|avi|ts|flv|rmvb|mov|wmv)$");

    private EpisodeNameUtil() {
    }

    public static String stripSizeAndExt(String name) {
        if (name == null || name.isEmpty()) return "";
        String stripped = SIZE.matcher(name).replaceFirst("");
        stripped = EXT.matcher(stripped).replaceFirst("");
        return stripped.trim();
    }
}