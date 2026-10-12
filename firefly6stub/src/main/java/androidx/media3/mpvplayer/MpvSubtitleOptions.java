package androidx.media3.mpvplayer;

/** 上游定制 media3 私有模块 MpvSubtitleOptions（mpv 字幕选项）。 */
public final class MpvSubtitleOptions {

    public final double position;
    public final double scale;

    private MpvSubtitleOptions(Builder builder) {
        this.position = builder.position;
        this.scale = builder.scale;
    }

    public static final class Builder {

        private double position;
        private double scale = 1.0;

        public Builder() {
        }

        public Builder setPosition(double position) {
            this.position = position;
            return this;
        }

        public Builder setScale(double scale) {
            this.scale = scale;
            return this;
        }

        public Builder setSecondarySubtitle(int trackId, float position, boolean forced) {
            return this;
        }

        public Builder setCustomStyle(
                int textColor,
                int backgroundColor,
                int edgeType,
                int edgeColor,
                float edgeWidth,
                float shadow) {
            return this;
        }

        public Builder setSystemCaptionStyle() {
            return this;
        }

        public MpvSubtitleOptions build() {
            return new MpvSubtitleOptions(this);
        }
    }
}
