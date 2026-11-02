package com.firefly.tv.bean;

import android.net.Uri;
import android.util.Log;

/**
 * Round 36（T-149/T-150/T-146）统一日志出口 —— 第五个调试 tag **FireFlyDetail**。
 *
 * 为什么要单独一个类：详情页（leanback/mobile 两个 DetailActivity）、播放页（两个 VideoActivity）、
 * 共享层 VodPlaybackController 都要打同一族日志，格式必须逐字一致，C 才能用固定前缀 grep；
 * 散在各 Activity 里各写一份必然漂移（本轮 5 条口径见 Test/test-task.md Round 36 第三节）。
 *
 * 脱敏铁律（A 派单硬约束 + 团队证据规则）：
 * **URL 只打 host + len，绝不打 path/query**——直链常带播放 token 与鉴权参数，落进 logcat 等于外泄。
 *
 * 分支必打原则：**每个分支各打一行，"没走到/被跳过"的分支也要有对应日志**，
 * 否则 C 看到 0 条日志时分不清"分支没走到"还是"代码根本没编进包"（红线 §二.11）。
 */
public final class DetailLog {

    /** 详情页/取图链路调试 tag（02 手册第六节 tag 清单本轮新增第 5 个）。 */
    public static final String TAG = "FireFlyDetail";

    private DetailLog() {
    }

    /**
     * 口径 1：取图优先级判定结果。**chosen=none 也必须打**，用于证明 pick 被调用过。
     *
     * @param chosen 命中的**来源档位**（{@code slide}=横版 / {@code pic}=竖版 / {@code none}=两者皆空须回落锁定海报）
     */
    public static void pick(int slideLen, int picLen, String chosen) {
        Log.d(TAG, "pic pick slideLen=" + slideLen + " picLen=" + picLen + " chosen=" + (chosen == null || chosen.isEmpty() ? "none" : chosen));
    }

    /** 口径 2a：图片字段被写入。 */
    public static void picSet(String url) {
        Log.d(TAG, "pic set " + mask(url));
    }

    /**
     * 口径 2b：图片字段写入被跳过。
     * reason 取值（三值固定，便于 C 逐项核对）：
     * {@code empty}=来图为空（无图可写）；{@code nolock}=没有 History 对象可写（页面未持有锁定底图）；
     * {@code same}=历史/收藏已有图（锁定优先，不覆盖）。
     */
    public static void picSkip(String reason, String url) {
        Log.d(TAG, "pic skip reason=" + reason + " " + mask(url));
    }

    /** 口径 3：背景图加载结果回调（允许重复打印，重试各一条）。 */
    public static void picLoaded(boolean ok) {
        Log.d(TAG, "pic loaded ok=" + ok);
    }

    /** 口径 4：详情页两态与源行数据自证（err 非空时输出 reason，供判搜索失败/为空）。 */
    public static void detailState(String mode, int sources, String cur, String err) {
        Log.d(TAG, "detailState mode=" + mode + " sources=" + sources + " cur=" + cur + (err == null ? "" : " reason=" + err));
    }

    /** 口径 5：焦点链迁移（DOWN/UP 各一条，id 用 View.getId()，与 T-176/177 的 focus=<id> 风格一致）。 */
    public static void focus(String dir, int id) {
        Log.d(TAG, "detail focus=" + id + " dir=" + dir);
    }

    /**
     * 口径 5b（T-192-4 / Round 40）：换源态"名单未到"的焦点暂存行。
     * 沿用 {@code dir=init} 前缀（写作 {@code dir=initHold}），C 原有 `grep dir=init` 仍能命中，口径不破坏。
     *
     * @param reason {@code noData}=进换源态时名单为空，焦点先存 play；
     *               {@code desc}=系统把焦点给了简介，本轮要求不得展开，已就地收回；
     *               {@code ready}=名单到齐，兑现"落当前源卡"
     */
    public static void focusHold(String reason, int id) {
        Log.d(TAG, "detail focus=" + id + " dir=initHold reason=" + reason);
    }

    /**
     * 口径 6（T-184 / Round 39）：详情页背景证据行——**由代码自算**，C 不依赖真机截图即可核对几何与亮度。
     * 字段口径（负值统一打 na）：
     * {@code src}=取图档位（slide=横版剧照优先命中 / pic=竖版海报 / none=无图）；
     * {@code fit}=主体层（bg，fitCenter）的适配档位（height=竖版按整高铺满，主体保留全高；
     * fill=≈16:9 整图铺满零裁；width=超宽按宽铺满）；
     * {@code anchor}=主体层在屏上的锚位（mid/top/bottom，fitCenter 恒为 mid）；
     * {@code scale}=主体层放大倍率（绘制高/源图高，两位小数；底层 bgFill 的倍率另见 dim 计算注释）；
     * {@code dim}=文字区（x≤1400、y320–900）**合成遮罩后**的平均亮度 0–255（判据 ≤58）；
     * {@code raw}=同区域遮罩**前**的平均亮度（Rec.601 luma，0–255，供反查遮罩是否够）；
     * {@code alpha}=同区域采样到的最小 alpha 通道值（255=图不带透明；<255 说明图源自带镂空/圆角，
     * 即 C 报的"圆角半透明面板"来源——本轮由不透明 bgFill 铺底消除）；
     * {@code fill}=**T-187-b（Round 40）新增**：铺底层（bgFill）本轮的实际状态
     * （{@code opaque}=横版两层重合 / {@code dim0.30}=竖版强压暗，只补信箱区的底、不再与主体层争画面 / {@code na}=无图或加载失败）。
     */
    public static void bgEvidence(String src, String fit, String anchor, String scale, int dim, int raw, int alpha, String fill) {
        Log.d(TAG, "bg src=" + src + " fit=" + fit + " anchor=" + anchor + " scale=" + scale
                + " dim=" + num(dim) + " raw=" + num(raw) + " alpha=" + num(alpha) + " fill=" + fill);
    }

    /**
     * 口径 6b（T-203 / β1 追加，C 建议）：**解码尺寸证据行**，专门回答"这张图到底多大、有没有被缩小"。
     * {@code iwRaw/ihRaw}=Glide 交回的 Drawable intrinsic 尺寸（进 ImageView 二次缩放**之前**）；
     * {@code tw/th}=目标视图尺寸（1920×1080 量级）；{@code ds}=数据来源
     * （{@code LOCAL}/{@code REMOTE}=网络，{@code DATA_SOURCE}=内存/磁盘缓存）。
     * 局限（如实标注，不当已解决）：intrinsic 仍受 Glide 按 target 的降采样影响，
     * 要拿"源文件真实宽高"必须改解码策略（dontDownsample）——那是行为改动，不在本轮范围。
     */
    public static void bgImg(int iwRaw, int ihRaw, int tw, int th, String ds) {
        Log.d(TAG, "bg img iwRaw=" + iwRaw + " ihRaw=" + ihRaw + " tw=" + tw + " th=" + th + " ds=" + ds);
    }

    /**
     * 口径 6c（T-203 / β1 追加，C 建议）：**候选图 URL 证据行**。
     * 按既有脱敏纪律只打 host+长度（{@link #mask}），不打完整 URL——用于判"slide 与 pic 是不是同一张、
     * 横版到底来自哪个站"。{@code chosen}=本次背景实际选中的档位。
     */
    public static void bgSrc(String slideUrl, String picUrl, String chosen, String chosenTier) {
        Log.d(TAG, "bg cand slide[" + mask(slideUrl) + "] pic[" + mask(picUrl) + "] chosen=" + chosen + " tier=" + chosenTier);
    }

    /** 脱敏：只保留 host + 长度；host 取不到（相对地址/data:）时打 host=none。null 安全。 */
    public static String mask(String url) {
        if (url == null || url.trim().isEmpty()) return "host=none len=0";
        String host;
        try {
            host = Uri.parse(url).getHost();
        } catch (Exception e) {
            host = null;
        }
        return "host=" + (host == null ? "none" : host) + " len=" + url.length();
    }

    /** 长度兜底（null 安全，供 pick 日志用）。 */
    public static int len(String s) {
        return s == null ? 0 : s.length();
    }

    /** 口径 6 的数值兜底：-1（不可测）一律打 na，避免 C 把 0 当"全黑"误读。 */
    private static String num(int value) {
        return value < 0 ? "na" : String.valueOf(value);
    }
}
