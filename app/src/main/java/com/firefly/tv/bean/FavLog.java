package com.firefly.tv.bean;

import android.util.Log;

/**
 * Round 40（T-186）收藏链路统一日志出口 —— tag 族第六个：**FireFlyFav**。
 *
 * 为什么要单独一个类：收藏的写入（详情页 toggleKeep）、就地修复（Keep.sanitize）、
 * 启动读取（HomeActivity / KeepActivity / LiveActivity 三处消费）分属不同模块，
 * 口径必须逐字一致，C 才能用固定前缀一次 grep 拿到全链证据（同 DetailLog 的成立理由）。
 *
 * 脱敏铁律（团队证据规则）：**key 与图片地址一律只打长度，绝不打明文**——
 * 收藏 key 形如 {@code site@@@id@@@cid}，含站点直连信息；vodPic 常带鉴权 query。
 *
 * 分支必打原则：坏数据被跳过、写库失败、整体查询抛异常，**每一种都要有自己的行**，
 * 否则 C 看到 0 条日志时分不清"没有坏数据"还是"根本没读到收藏"。
 */
public final class FavLog {

    /** 收藏链路调试 tag（02 手册 tag 清单本轮新增第 6 个）。 */
    public static final String TAG = "FireFlyFav";

    private FavLog() {
    }

    /**
     * 口径 1：收藏写/删。
     *
     * @param action {@code add} / {@code remove}
     * @param ok     写库是否成功（false 时 err 必非空）
     * @param key    收藏 key（只取长度，不打明文）
     * @param pic    海报地址（只取长度；len=0 即"空图收藏"，本轮允许并需可见）
     * @param err    异常类名，正常时传 null
     */
    public static void write(String action, boolean ok, String key, String pic, String err) {
        Log.d(TAG, "fav action=" + action + " ok=" + ok + " keyLen=" + len(key)
                + " picLen=" + len(pic) + (err == null ? "" : " err=" + err));
    }

    /**
     * 口径 2：启动/页面加载收藏（T-186 第 4 条判据——单条坏数据只跳过，不得阻断启动）。
     *
     * @param kind   数据类别，{@code vod}=影视收藏 / {@code live}=直播收藏
     * @param ok     整体查询是否成功（false=DB 读取抛异常，此时 count=0，调用方照常启动）
     * @param count  通过校验、交给 UI 的条数
     * @param skip   被跳过的坏条数
     * @param fixed  就地修复并回写成功的条数
     * @param err    异常类名，正常时传 null
     */
    public static void load(String kind, boolean ok, int count, int skip, int fixed, String err) {
        Log.d(TAG, "fav action=load kind=" + kind + " ok=" + ok + " count=" + count
                + " skip=" + skip + " fixed=" + fixed + (err == null ? "" : " err=" + err));
    }

    /**
     * 口径 3：单条坏数据的跳过/修复明细。
     *
     * @param action {@code skip}=丢弃该条不入 UI / {@code fix}=就地修复后保留
     * @param reason 取值（固定三档，便于 C 对号）：{@code nullKey}=主键缺失（不可修）/
     *               {@code nullField}=可空字段为 null（可修）/ {@code bad}=解包抛异常
     * @param key    出问题的 key（只取长度）
     * @param err    异常类名，非异常路径传 null
     */
    public static void item(String action, String reason, String key, String err) {
        Log.d(TAG, "fav action=load " + action + " reason=" + reason + " keyLen=" + len(key)
                + (err == null ? "" : " err=" + err));
    }

    /**
     * 口径 4：收藏行**装配**结果（首页收藏行 / 收藏页列表）。
     * 与口径 2 的分工：2=数据层读出并洗好，4=UI 层把列表挂上去有没有成。
     * P0 判据"坏数据不阻断启动"看这条：出现 ok=false 也只代表收藏行没显示，Activity 必须照常起来。
     *
     * @param kind {@code home} / {@code keep}
     * @param ok   装配是否完成（false 时 err 必非空，且异常已就地吞掉，不外抛）
     */
    public static void ui(String kind, boolean ok, String err) {
        Log.d(TAG, "fav action=ui kind=" + kind + " ok=" + ok + (err == null ? "" : " err=" + err));
    }

    /**
     * 口径 5（T-212 / β1）：换源过滤结果——收藏/历史在「当前源」口径下滤掉多少。
     * 刘先生判据「换了源后，历史与收藏都不显示」需要可核对证据，否则 C 只能靠截图数卡片。
     * 站点键只打长度（脱敏口径同 {@link #write}）；{@code home=na} 表示未配置首页源、本轮不过滤。
     *
     * @param kind {@code vod} / {@code history}
     * @param in   该仓库下的原始条数
     * @param out  过滤后进入展示的条数
     */
    public static void filter(String kind, boolean homeKnown, int homeLen, int cid, int in, int out) {
        Log.d(TAG, "fav action=filter kind=" + kind + " home=" + (homeKnown ? "set" : "na")
                + " homeLen=" + homeLen + " cid=" + cid + " in=" + in + " out=" + out);
    }

    /** 长度兜底（null 安全）。 */
    public static int len(String s) {
        return s == null ? 0 : s.length();
    }
}
