package com.firefly.tv.bean;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * β25 / T-332（A 开工令 2026-10-31，边界已定）：源侧 UGC 富文本宏的**显示层清洗**。
 *
 * 病灶：部分源站把导演/演员存成可点击富文本宏
 * {@code [a=cr:{"url":"/voddetail/xx","name":"张三","t":0}]张三[/a]}，
 * 详情页人员行原样显示 ⇒ 用户看到整串宏噪声。
 *
 * 边界（A 已定，逐字执行）：
 * 1. **成对宏**：优先取宏属性 JSON 里的 {@code name}；JSON 解析失败或 name 为空 ⇒ 取宏内正文；
 * 2. **不成对残留** {@code [a=…} 开壳与 {@code [/a]} 闭壳：一律剥掉；
 * 3. 清洗只放 **getter 层**（{@link Vod#getDirector()}/{@link Vod#getActor()}），**不改库存数据**；
 * 4. **纯文本必须逐字不变**（360zy 等正常源：不含 {@code [a=} 的字符串直接原样返回）——
 *    逗号规整只在**确实做过宏替换**后执行，普通串的标点零触碰。
 */
public final class UgcTags {

    /** 成对宏：[a=cr:{JSON}]正文[/a]；非贪婪、JSON 不含花括号嵌套（源侧实测扁平对象）。 */
    private static final Pattern PAIR = Pattern.compile("\\[a=([^\\]]*)\\](.*?)\\[/a\\]");

    /** 不成对残留壳：开壳 [a=… 到行尾或下一个 [ 前；闭壳 [/a]。 */
    private static final Pattern OPEN = Pattern.compile("\\[a=[^\\]]*");
    private static final Pattern CLOSE = Pattern.compile("\\[/a\\]");

    private UgcTags() {
    }

    /** null 安全入口；空串原样回空串。 */
    public static String clean(String raw) {
        if (raw == null || raw.isEmpty()) return raw == null ? "" : raw;
        if (!raw.contains("[a=") && !raw.contains("[/a]")) return raw;
        String out = replacePairs(raw);
        out = OPEN.matcher(out).replaceAll("");
        out = CLOSE.matcher(out).replaceAll("");
        out = collapseCommas(out);
        return out.trim();
    }

    private static String replacePairs(String raw) {
        Matcher m = PAIR.matcher(raw);
        // β31 修 P0：`Matcher.appendReplacement(StringBuilder,String)` 是 Java 9 / 较新 Android 才有的重载，
        // 本机 core-oj 没有 ⇒ 真机 `NoSuchMethodError`（DOCTORX 一开就崩）。
        // 改用 **StringBuffer** 重载（API 1 起就有），行为完全一致。
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(resolve(m.group(1), m.group(2))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** name 优先，解析失败/空 name 退正文；正文也为空则空串（后续逗号规整会收掉空位）。 */
    private static String resolve(String attrs, String body) {
        String b = body == null ? "" : body.trim();
        String name = nameOf(attrs);
        if (!name.isEmpty()) return name;
        return b;
    }

    /** 属性串形如 {@code cr:{...}}；截出花括号子串交 org.json 取 name，任何异常都按"没有 name"。 */
    private static String nameOf(String attrs) {
        if (attrs == null) return "";
        int s = attrs.indexOf('{');
        int e = attrs.lastIndexOf('}');
        if (s < 0 || e <= s) return "";
        try {
            String name = new JSONObject(attrs.substring(s, e + 1)).optString("name").trim();
            return name;
        } catch (Exception ex) {
            return "";
        }
    }

    /** 宏替换留下的空位（连续逗号、尾逗号）收干净；仅替换支路调用，普通串不经此步。 */
    private static String collapseCommas(String s) {
        String out = s;
        String prev;
        do {
            prev = out;
            out = out.replaceAll(",\\s*,", ",");
        } while (!out.equals(prev));
        return out.replaceAll("[,，]\\s*$", "");
    }
}
