package com.firefly.tv.bean;

/**
 * T-149（拍板 47）：详情页/播放页背景取图优先级的**纯函数**。
 *
 * 设计约束（Round 36 验收标准 T-149⑤）：
 * 1. **零 Android 依赖**——不 import TextUtils/Log/Uri 等任何 android/androidx 类，
 *    因此测试运维（C）可直接 `javac` 单独编译并跑六例单测，无需新增 Gradle test 任务；
 * 2. 只做"选哪张图"的判定，不做加载、不做日志（日志由调用方打，见 FireFlyDetail 口径 1）；
 * 3. 语义：**横版优先 → 竖版兜底 → 都没有则返回 null**（null 表示调用方须回落"锁定海报"，
 *    即拍板 11 的页面级缓存图，不能在这里回退成空串，否则调用方分不清"没图"和"图是空串"）。
 *
 * 空白判定按单测表要求：null / "" / 全空白 三者都视为"无横版"（slide 全空白 → 取 pic）。
 */
public final class DetailPic {

    private DetailPic() {
    }

    /**
     * @param slide 横版图地址（源字段 vod_pic_slide / XML pic_slide，可能为 null）
     * @param pic   竖版/列表图地址（源字段 vod_pic，可能为 null）
     * @return 命中的图地址（trim 后）；两者皆空返回 null
     */
    public static String pick(String slide, String pic) {
        if (!isEmpty(slide)) return slide.trim();
        if (!isEmpty(pic)) return pic.trim();
        return null;
    }

    /** 是否为横版图（决定 Glide 用 centerInside 直铺还是 centerCrop 裁切）；null/空白按"非横版"处理。 */
    public static boolean isSlide(String chosen, String slide) {
        return !isEmpty(slide) && slide.trim().equals(chosen);
    }

    /** 零依赖空白判定（等价 TextUtils.isEmpty，但不引 android 类）。 */
    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
