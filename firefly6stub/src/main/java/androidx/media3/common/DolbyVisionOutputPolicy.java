package androidx.media3.common;

/**
 * 上游定制 media3 私有类 DolbyVisionOutputPolicy 的替代实现（stub）。
 *
 * <p>官方 media3 无此类（上游 项目私有 aar 内，源码未公开且官方 APK 中已被混淆）。
 * 此处提供最小枚举语义：AUTO（默认，由播放器自动处理）与 ASSUME_UNSUPPORTED，
 * 满足 DecodeSetting 的 get/put 与范围校验逻辑。老设备（API 23）无杜比视界能力，
 * AUTO 即合理行为。
 */
public final class DolbyVisionOutputPolicy {

    public static final int AUTO = 0;
    public static final int ASSUME_UNSUPPORTED = 2;

    @androidx.annotation.IntDef({AUTO, ASSUME_UNSUPPORTED})
    public @interface Mode {}

    private DolbyVisionOutputPolicy() {
    }
}
