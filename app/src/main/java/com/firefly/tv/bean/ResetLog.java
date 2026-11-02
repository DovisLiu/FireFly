package com.firefly.tv.bean;

import android.text.TextUtils;
import android.util.Log;

import com.firefly.tv.utils.UrlUtil;

/**
 * β56 / TV-03 崩溃页「清除源配置」的留痕出口 —— tag 族第十一个：<b>FireFlyReset</b>。
 * <p>
 * 为什么单独一个类：这个动作会<b>删数据</b>，而删的是用户自己填的源地址。出事时必须能一眼看出
 * 「到底删了哪一条、删掉几条、还剩几条」，否则用户与我们都无法区分「收窄成一条」与「整表清了」。
 * <p>
 * 脱敏铁律：<b>URL 只打 host 与长度</b>——源地址常带 token／签名／多仓清单参数，一律不落明文；
 * host 走现成的 {@link UrlUtil#host(String)}，不另写一份解析。
 */
public final class ResetLog {

    /** 清源动作调试 tag（02 手册 tag 清单本轮新增第 11 个）。 */
    public static final String TAG = "FireFlyReset";

    /** 收窄后的固定值：本包只删点播当前这一条，type 恒 0。 */
    private static final String SCOPE = "reset scope=one type=0";

    private ResetLog() {
    }

    /**
     * 真的删掉了当前点播源那一条。
     *
     * @param deleted    本次实际删掉的行数（用删除前后 findByType(0) 的条数差算，不新增 DAO 方法）
     * @param remainVod  删除后点播源历史剩余条数（type 0）
     * @param remainLive 直播源条数（type 1）——本包对它<b>零改动</b>，打出来是为了自证"没被连带删"
     */
    public static void one(String url, int deleted, int remainVod, int remainLive) {
        Log.d(TAG, SCOPE + " url=" + host(url) + " len=" + len(url) + " deleted=" + deleted
                + " remainVod=" + remainVod + " remainLive=" + remainLive);
    }

    /** 当前源 url 为空（无当前源）⇒ <b>什么都不删、Prefers 也不动</b>，只留痕，避免把"没源"误报成"清过了"。 */
    public static void skipNoUrl(int remainVod, int remainLive) {
        Log.d(TAG, SCOPE + " skip reason=nourl remainVod=" + remainVod + " remainLive=" + remainLive);
    }

    /** 取不到 host 就报 nonhost——日志绝不允许自己再抛一次。 */
    private static String host(String url) {
        if (TextUtils.isEmpty(url)) return "-";
        try {
            String value = UrlUtil.host(url);
            return TextUtils.isEmpty(value) ? "nonhost" : value;
        } catch (Exception e) {
            return "nonhost";
        }
    }

    private static int len(String url) {
        return url == null ? 0 : url.length();
    }
}
