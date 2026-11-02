package com.firefly.tv.utils;

import android.graphics.Bitmap;

/**
 * β11 / 组B 三轮（A 定位 ⇒ 我复核 ⇒ 改「接缝连续」）。
 * <p>
 * <b>为什么加宽羽化根本救不了</b>（β10 实测：逐行 6/35 超 P50×1.05、x=1120 单列峰值 70 次、肉眼仍有竖线）：
 * β8–β10 的延展层是「同一张竖图 centerCrop 到 480×270 再模糊」后 {@code fitXY} 铺满 1920×1080。
 * 竖图是 2:3~3:4 之类，centerCrop 到 16:9 时**横向是满幅**（只裁掉上下），所以屏幕 x 列显示的是原图
 * {@code x/screenW} 那一列。接缝在屏 x≈1120（屏宽 1920）⇒ 背景在接缝处显示的是**原图约 58.3% 处**的内容，
 * 而主体层首列显示的是**原图 0% 处**（主体＝整高右贴边，左缘就是原图第一列）
 * ⇒ 两侧本来就不是同一块图像，色差是内容差，加宽羽化只把台阶抹成斜坡，抹不掉错位本身。
 * <p>
 * <b>本类的做法＝方案 A（edge-stretch）的「接缝严格连续」变体**：
 * 延展层＝**主体左缘那一列**（即解码图的第 0 列）横向复制到全屏，纵向保持与原图同样的线性映射
 * ⇒ 接缝处的背景像素与主体首列**同源同值**（差值只来自 ImageView 的双线性取样，量级 ≤1~2/255），
 * 这是构造性保证，不是调参调出来的。
 * <b>不叠模糊</b>：纵向模糊会让接缝处的值变成「该列的邻域均值」，与 A 的新硬指标
 * 「接缝列与主体首列 RGB 平均差 ≤2/255」直接冲突（列内容越竖直变化大，超得越多）；
 * 而"没有内容纹理"这个目的本来就由**横向只有一列**天然满足——延展层在水平方向是常数，不可能出现可辨内容。
 * A 方案里「叠重度模糊」的位置换成「逐列亮度斜坡」：
 *   · 接缝左侧 {@code plateauPx} 宽内 k≡1.0（**这一段是连续性的来源，不能压暗**）；
 *   · 再向左 {@code rampPx} 线性降到 {@code kMin}；
 *   · 更左恒 {@code kMin}。
 * {@code kMin} 不是拍死的数：按「左区平均亮度 ≈ TARGET_RATIO × 全图平均亮度」闭式解出来（见 {@link #solveKMin}），
 * 因为延展层内容变了（不再是整图均值而是左缘列），沿用旧的固定 0.50 会让亮度比随源漂移。
 * 最终亮度仍由 {@code shape_detail_scrim} 的水平遮罩叠加（左浓右淡、止于屏宽 58% ⇒ 恰好不压接缝平台）。
 * <p>
 * 只在后台线程调用（{@code Task.execute}）；产物是 96×h 的小图（h＝解码图高，≤约 1400 ⇒ ≤ 0.5 MB），
 * 放大交给 GPU 的 {@code fitXY} ⇒ 不分配 1920×1080 位图。
 */
public final class PosterWash {

    private PosterWash() {
    }

    /** 一次构建的产物＋给日志/自算采样用的统计量。 */
    public static final class Wash {
        public final Bitmap bitmap;
        public final int seamX;
        public final float kMin;
        public final int colMean;
        public final int srcMean;
        public final float avgK;

        Wash(Bitmap bitmap, int seamX, float kMin, int colMean, int srcMean, float avgK) {
            this.bitmap = bitmap;
            this.seamX = seamX;
            this.kMin = kMin;
            this.colMean = colMean;
            this.srcMean = srcMean;
            this.avgK = avgK;
        }
    }

    /**
     * @param src       **正在显示的那张**解码图（不是另一次下载的缩略图 ⇒ 接缝同值才有保证）
     * @param width     产物宽（列数；水平方向是常数，几十个列就够表达斜坡）
     * @param screenW   屏宽（把产物列号换算成屏幕列号，用于对位接缝）
     * @param seamX     接缝屏幕列＝主体层左缘
     * @param plateauPx 接缝左侧的全亮平台宽（连续性来源）
     * @param rampPx    平台向左到 kMin 的斜坡宽
     * @param target    左区/全图 平均亮度目标（判据上限 0.55，这里取更严的 0.50）
     * @return 构建结果；入参非法或异常返回 null（调用方按「没有延展层」处理，绝不影响主体显示）
     */
    public static Wash build(Bitmap src, int width, int screenW, int seamX,
                             int plateauPx, int rampPx, float target) {
        if (src == null || src.isRecycled() || width <= 1 || screenW <= 0 || seamX <= 0) return null;
        try {
            int h = src.getHeight();
            int iw = src.getWidth();
            if (h <= 0 || iw <= 0) return null;
            // 主体左缘那一列（整列）——注意取的是**正在显示的这张位图**，与主体层同源
            int[] col = new int[h];
            src.getPixels(col, 0, 1, 0, 0, 1, h);
            int colMean = meanLuma(col, 0, 1);
            int srcMean = meanSourceLuma(src);
            float plateau = Math.min(plateauPx, seamX);
            float ramp = Math.min(rampPx, Math.max(0, seamX - plateau));
            float kMin = solveKMin(target, colMean, srcMean, plateau / seamX, ramp / seamX);
            Bitmap out = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
            int[] px = new int[width * h];
            float colToScreen = screenW / (float) width;
            // 逐列只改亮度，纵向仍逐行取 col[y] ⇒ 列内容原样保留（接缝同值的来源）
            for (int x = 0; x < width; x++) {
                float k = factor(Math.round(x * colToScreen), seamX, kMin, (int) plateau, (int) ramp);
                for (int y = 0; y < h; y++) {
                    int c = col[y];
                    int rr = clamp255(Math.round(((c >> 16) & 0xFF) * k));
                    int gg = clamp255(Math.round(((c >> 8) & 0xFF) * k));
                    int bb = clamp255(Math.round((c & 0xFF) * k));
                    px[y * width + x] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
                }
            }
            out.setPixels(px, 0, width, 0, 0, width, h);
            float avgK = avgFactor(seamX, kMin, (int) plateau, (int) ramp);
            return new Wash(out, seamX, kMin, colMean, srcMean, avgK);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 逐列亮度系数：接缝左侧平台恒 1.0，再向左线性降到 kMin，更左恒 kMin。
     * 「平台」必须**贴到接缝**且系数为 1，否则接缝处的背景值不再等于主体首列 ⇒ A 的 ≤2/255 就守不住。
     */
    static float factor(int screenX, int seamX, float kMin, int plateauPx, int rampPx) {
        int plateauStart = seamX - plateauPx;          // 平台左端
        int rampStart = plateauStart - rampPx;         // 斜坡左端（更左恒 kMin）
        if (screenX >= plateauStart) return 1f;
        if (screenX <= rampStart || rampPx <= 0) return kMin;
        float t = (screenX - rampStart) / (float) rampPx;   // 0 在左端、1 在平台端
        return kMin + (1f - kMin) * t;
    }

    /**
     * 闭式解 kMin：左区平均系数 {@code avgK = p + r/2 + k·(1 − p − r/2)}（p＝平台占比、r＝斜坡占比），
     * 要求「左区平均亮度 ≈ target × 全图平均亮度」⇒ {@code avgK = target·srcMean/colMean}，反解 k。
     * 解出界时按 [K_MIN_FLOOR, 1] 夹住（夹住意味着该源无法同时满足两个判据，日志会如实暴露 kMin 与 avgK）。
     */
    static float solveKMin(float target, int colMean, int srcMean, float plateauFrac, float rampFrac) {
        final float floor = 0.12f;
        float wantAvg = colMean <= 0 ? target : (target * srcMean / (float) colMean);
        float denom = 1f - plateauFrac - rampFrac / 2f;
        if (denom <= 0.01f) return floor;
        float k = (wantAvg - plateauFrac - rampFrac / 2f) / denom;
        return k < floor ? floor : Math.min(k, 1f);
    }

    /** 与 factor() 同一几何的左区平均系数（供日志核对；解析式，不再重采样）。 */
    static float avgFactor(int seamX, float kMin, int plateauPx, int rampPx) {
        float p = Math.min(plateauPx, seamX) / (float) seamX;
        float r = Math.min(rampPx, Math.max(0, seamX - plateauPx)) / (float) seamX;
        return p * 1f + r * (1f + kMin) / 2f + (1f - p - r) * kMin;
    }

    /** 全图平均亮度：按 4 倍步长抽样（≤约 3400 个采样点，够稳且几毫秒）。 */
    private static int meanSourceLuma(Bitmap src) {
        int iw = src.getWidth();
        int ih = src.getHeight();
        int stepX = Math.max(1, iw / 40);
        int stepY = Math.max(1, ih / 80);
        long sum = 0;
        int n = 0;
        for (int y = 0; y < ih; y += stepY) {
            for (int x = 0; x < iw; x += stepX) {
                int c = src.getPixel(x, y);
                sum += luma(c);
                n++;
            }
        }
        return n == 0 ? 0 : (int) (sum / n);
    }

    private static int meanLuma(int[] px, int off, int stride) {
        long sum = 0;
        int n = 0;
        for (int i = off; i < px.length; i += Math.max(1, stride)) {
            sum += luma(px[i]);
            n++;
        }
        return n == 0 ? 0 : (int) (sum / n);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** Rec.601 luma（γ 编码值直接取平均），与 DetailActivity 证据行同口径。 */
    private static int luma(int color) {
        return (int) (0.299f * ((color >> 16) & 0xFF) + 0.587f * ((color >> 8) & 0xFF) + 0.114f * (color & 0xFF));
    }
}
