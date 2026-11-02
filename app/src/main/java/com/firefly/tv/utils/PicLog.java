package com.firefly.tv.utils;

import android.net.Uri;
import com.bumptech.glide.load.HttpException;
import com.bumptech.glide.load.engine.GlideException;
import android.text.TextUtils;
import android.util.Log;

/**
 * β51 / TV-04 首页补图与图片黑名单的统一日志出口 —— tag 族第七个：**FireFlyPic**。
 * <p>
 * 为什么单独一个类：补图的触发在取数层（{@code SiteApi.fetchPic}）、黑名单的命中在图片层
 * （{@code ImgUtil.load}），两处隔着不同包，判据字符串必须逐字一致，C 才能一次 grep 拿到全链证据
 * （成立理由与 FavLog 相同）。
 * <p>
 * 脱敏铁律：**站点地址与图片 URL 一律只打 host＋长度**——图片 URL 常带鉴权 query 与签名，
 * 列表请求的 ids 也只打长度。
 * <p>
 * 分支必打原则：补图成功、被同站间隔拦下、补图失败、黑名单加入 / 到期放行 / 窗口内被拦，
 * 每一种都必须有自己的行；否则 C 看到 0 条日志时分不清"没有坏图"还是"根本没触发"。
 */
public final class PicLog {

    /** 补图与图片黑名单链路调试 tag（02 手册 tag 清单本轮新增第 7 个）。 */
    public static final String TAG = "FireFlyPic";

    private PicLog() {
    }

    /**
     * 一次补图请求的留痕（发出后打，含失败以外的全部出口）。
     *
     * @param siteKey 站点 key（用户自填的短标签，不是 URL）
     * @param batch   本次列表总条数
     * @param empty   其中 pic 为空的条数
     * @param ids     实际带出去的 id 条数（受每批上限约束，必然 ≤ empty）
     * @param filled  真正回填上图的条数（detail 没返回这些 id 时必然 &lt; ids）
     * @param costMs  这一腿耗时毫秒——A 要的三个实测值之一，靠它校准每批上限
     */
    public static void fetch(String siteKey, int batch, int empty, int ids, int filled, long costMs) {
        Log.d(TAG, "pic fetch site=" + safe(siteKey) + " batch=" + batch + " empty=" + empty
                + " ids=" + ids + " filled=" + filled + " cost=" + costMs);
    }

    /** 补图被同站最小间隔拦下（口径 T2）：不发请求、原列表照出。 */
    public static void throttleSkip(String siteKey, long sinceMs) {
        Log.d(TAG, "pic fetch skip reason=throttle site=" + safe(siteKey) + " since=" + sinceMs);
    }

    /**
     * 补图这一腿失败。A 批的硬约束：<b>就地吞掉、返回原列表，绝不向上抛</b>——
     * 这一腿与首页整行共用同一个 30 秒预算（{@code ViewModelTaskRunner} 的 withTimeout 套在整个
     * callable 上），抛上去就不是"几张片没图"而是"整行推荐不出"。
     * 同时<b>绝不写进图片黑名单</b>：列表接口失败 ≠ 图片 URL 失败，否则一次整站抖动能把全站图拉黑。
     *
     * @param idsLen  本次 ids 参数的字符长度（只打长度）
     * @param err     异常类名，不打 URL 明文与消息体
     */
    public static void fetchFail(String apiUrl, int idsLen, String err) {
        Log.d(TAG, "pic fetch fail host=" + host(apiUrl) + " len=" + idsLen + " err=" + err);
    }

    /**
     * 图片黑名单状态变化。
     *
     * @param action   {@code add}=一次加载失败被记 / {@code retry}=窗口到期放行一次 /
     *                 {@code hit}=窗口内被拦（不发请求，这就是"到期前零请求"的证据；同一 URL 一个窗口内只打一次，免刷屏）
     * @param windowMs 本次记录的窗口长度（基值 10 分钟、连续失败翻倍、上限 60 分钟）
     * @param size     当前表内条数（表上限 200 条 LRU）
     */
    public static void blacklist(String action, String url, long windowMs, int size) {
        Log.d(TAG, "pic blacklist action=" + action + " host=" + host(url) + " window=" + windowMs + " size=" + size);
    }

    /**
     * β61 / TV-04：坏域名表（host 级 TTL）的每一次状态变化。<b>四态缺一不可</b>——
     * 真机看到「没有 pic host bad」时分不清「从没失败过」与「失败了但表没生效」，β83 那一整轮就是被这个坑住的。
     * <p>
     * {@code mark}＝一次连接层失败把该域名记坏 60 分钟；
     * {@code use}＝入口发现原始域名在表里 ⇒ 首发直接改写走镜像（这条就是"不再白等"的正面证据）；
     * {@code expire}＝窗口到期、该条目被清除 ⇒ 之后允许再试一次原域名（豆瓣修好了能自动恢复）；
     * {@code fallback-all}＝池里候选全在表里 ⇒ 退回原始 url 正常发一次（宁可慢一次，不能不出图）。
     * <p>
     * 脱敏照旧：只打 host（域名本身），不带路径与宏尾巴；{@code size}＝<b>当刻</b>表内条目数，
     * 与 {@code pic blacklist} 那条同一口径（会因 expire 回落，不是累计）。
     *
     * @param action {@code mark}／{@code use}／{@code expire}／{@code fallback-all}
     * @param size   当前表内域名数
     */
    public static void hostBad(String host, String action, int size) {
        Log.d(TAG, "pic host bad host=" + safe(host) + " action=" + action + " size=" + size);
    }

    /**
     * β55 / TV-04 诊断（A 批的一行日志，复用 beta80 号）：图片请求「真的发出过」且失败时打一行，带 HTTP 码。
     * <p>
     * 状态码是结构化取到的，不是从异常消息里抠的：实测 okhttp3-integration 5.0.9 的
     * {@code OkHttpStreamFetcher.onResponse} 在非 2xx 时 new 出 {@code com.bumptech.glide.load.HttpException}
     * （字节码里可见 {@code Response.code()}），该类对外只有 {@code getStatusCode()} 与 {@code UNKNOWN}。
     * <p>
     * 分工（A 第四问的答复）：本方法只在 {@code onLoadFailed} 里被调 ⇒ 有这行就等于「请求发出去了」；
     * 窗口内被我们自己的 TTL 黑名单拦下时压根不会走到 Glide（那条是 {@code action=hit}），
     * 所以「没发请求」与「发了被拒」分属两串日志，不靠这一行硬分。
     *
     * @param first isFirstResource；false 表示命中 Glide 缓存（缓存只存成功结果，失败不入缓存）
     * <p>
     * beta82 / TV-04：真机实测发现 code=none 且 getCauses() 为空时只剩一个类名、定不了因
     * （Glide 在请求<b>构造/派发</b>阶段就抛时往往没有 HTTP 码也没有 cause），
     * 故再补两列：{@code msg=}（异常消息，去掉换行、截断 120 字符，不撑成多段）
     * 与 {@code rc=}（{@code getRootCauses().size()}，用来分辨“真没 cause”与“cause 藏在 root 链里”。
     */
    public static void imgFail(String url, String name, GlideException e, boolean first) {
        int code = HttpException.UNKNOWN;
        String err = e == null ? "no-exception" : e.getClass().getSimpleName();
        if (e != null && !e.getCauses().isEmpty()) {
            err = e.getCauses().get(0).getClass().getSimpleName();
            for (Throwable cause : e.getCauses()) {
                if (cause instanceof HttpException) {
                    code = ((HttpException) cause).getStatusCode();
                    break;
                }
            }
        }
        Log.d(TAG, "pic img fail host=" + host(url) + " code=" + (code == HttpException.UNKNOWN ? "none" : code)
                + " err=" + err + " first=" + first + " rc=" + (e == null ? -1 : e.getRootCauses().size())
                + " msg=" + flat(e == null ? null : e.getMessage()) + " name=" + head(name));
    }


    /**
     * β59 / TV-04：豆瓣镜像域名重试的留痕（<b>一卡一次</b>，只由失败触发）。
     * <p>
     * result <b>四态</b>（β84 补第四态）各有分工，缺一条 C 就无法区分「没触发重试」与「触发了但没救回来」：
     * {@code ok}＝换域名取到图（原始 url 的退避记录整条删掉）；
     * {@code fail}＝换域名仍失败（此时按 β51 的翻倍把原始 url 推到 20 分钟，镜像 url 始终不入表）；
     * {@code skip}＝runnable 执行时发现 view 已被复用给别的条目，主动放弃重试（不抢新条目的请求）；
     * {@code err}＝重试<b>连发起都没成功</b>，打的是 {@code into()} 那一段抛出的异常（β83 的真机现场就是这一态没日志，
     * 异常被 {@code printStackTrace} 吞进 {@code System.err}，导致「包内代码在跑、FireFlyPic 一条 retry 都没有」）。
     * <p>
     * 脱敏照旧：<b>from 与 to 都只打 host</b>，URL 明文与宏尾巴（Referer/UA）不进日志。
     *
     * @param fromUrl 失败的那条原始 URL（只取 host）
     * @param toUrl   换到的镜像 URL（只取 host）
     * @param name    片名（截前 6 字，放整行末尾）
     * @param result  {@code ok}／{@code fail}／{@code skip}／{@code err}
     * @param e       仅 {@code fail}（GlideException）与 {@code err}（任意 Throwable）时非空，打异常类名；
     *                R8 之后 {@code fail} 那档是混淆名，判读仍以 {@code pic img fail} 的 rc=/msg= 为准
     */
    public static void imgRetry(String fromUrl, String toUrl, String name, String result, Throwable e) {
        Log.d(TAG, "pic img retry from=" + host(fromUrl) + " to=" + host(toUrl) + " name=" + head(name)
                + " result=" + result + (e == null ? "" : " err=" + e.getClass().getSimpleName()));
    }

    /** 异常消息可能多行（Glide 会把 cause 拼进 message）：换行与制表符统一打平，再截断 120 字符。 */
    private static String flat(String value) {
        if (TextUtils.isEmpty(value)) return "-";
        String oneLine = value.replace("\r", " ").replace("\n", " ").replace("\t", " ").trim();
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 120);
    }

    /** 片名只取前 6 字（够与稳定屏那张卡对上）；放整行最后，名字带空格也不截断前面的判据。 */
    private static String head(String value) {
        if (TextUtils.isEmpty(value)) return "-";
        return value.length() <= 6 ? value : value.substring(0, 6);
    }

    private static String safe(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    /** 只取 host；取不到就报 nonhost——日志绝不允许自己再抛一次。 */
    private static String host(String url) {
        if (TextUtils.isEmpty(url)) return "-";
        try {
            String value = Uri.parse(url).getHost();
            return TextUtils.isEmpty(value) ? "nonhost" : value;
        } catch (Exception e) {
            return "nonhost";
        }
    }
}
