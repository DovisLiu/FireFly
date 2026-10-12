package androidx.media3.mpvplayer.audio;

/**
 * 上游定制 media3 私有模块 androidx.media3.mpvplayer.audio.MpvAudioFilter 的空实现（stub）。
 *
 * <p>作为函数式接口（单一抽象方法 {@link #onAudioEffect()}），可接收 app 源码的
 * {@code effect::applyAudioEffect} 方法引用（() -> void）。{@link #EMPTY} 为空实现常量，
 * {@link Builder} 链式构造对应 {@code MpvAudioEffectFilter.create()} 调用。
 *
 * <p>因 MpvPlayer.isAvailable()=false，本 stub 永不实际执行音频滤镜逻辑。
 */
public interface MpvAudioFilter {

    /** 函数式接口方法，匹配 () -> void 的方法引用（effect::applyAudioEffect）。 */
    void onAudioEffect();

    MpvAudioFilter EMPTY = () -> {
    };

    final class Builder {

        public Builder addLoudnessNormalization(String id, double target, double gating, double ceiling) {
            return this;
        }

        public Builder addCompressor(String id, float threshold, float ratio, float makeup, float mix) {
            return this;
        }

        public Builder addVolume(String id, double volume) {
            return this;
        }

        public Builder addEqualizer(String id, double frequencyKHz, double gain) {
            return this;
        }

        public Builder addLimiter(String id, double ceiling) {
            return this;
        }

        public Builder addRuntimeChannelMix(String id, float[][] mix) {
            return this;
        }

        public MpvAudioFilter build() {
            return EMPTY;
        }
    }
}
