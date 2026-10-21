package com.firefly.tv.ui.custom;

import android.content.Context;

import com.firefly.tv.App;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 离线拼音候选引擎：按拼音前缀匹配 GB2312 一级常用字（3755 字，约 17KB 索引）。
 * 数据源 mozillazg/pinyin-data（MIT License），索引生成于 2026-10-18。
 */
public final class PinyinEngine {

    private static volatile PinyinEngine instance;

    /** 拼音 -> 该读音汉字串（GB2312 区位序≈常用序） */
    private final Map<String, String> table = new TreeMap<>();
    private static final int MAX_CANDIDATES = 9;

    private PinyinEngine(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("pinyin_index.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int split = line.indexOf(':');
                if (split > 0) table.put(line.substring(0, split), line.substring(split + 1));
            }
        } catch (Exception ignored) {
        }
    }

    public static PinyinEngine get() {
        if (instance == null) {
            synchronized (PinyinEngine.class) {
                if (instance == null) instance = new PinyinEngine(App.get());
            }
        }
        return instance;
    }

    /** 按拼音前缀匹配候选字：完全匹配的读音组优先，其余按读音长度升序（更精确优先），去重后取前 9 个。计算量极小（405 组线性扫），主线程可直接调用。 */
    public List<String> match(String prefix) {
        List<String> result = new ArrayList<>();
        if (prefix == null || prefix.isEmpty() || table.isEmpty()) return result;
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        List<String> keys = new ArrayList<>();
        for (String key : table.keySet()) {
            if (key.equals(prefix)) keys.add(0, key);
            else if (key.startsWith(prefix)) keys.add(key);
        }
        keys.sort((a, b) -> a.length() - b.length());
        for (String key : keys) {
            for (char ch : table.get(key).toCharArray()) {
                if (merged.size() >= MAX_CANDIDATES) break;
                merged.add(String.valueOf(ch));
            }
            if (merged.size() >= MAX_CANDIDATES) break;
        }
        result.addAll(merged);
        return result;
    }
}
