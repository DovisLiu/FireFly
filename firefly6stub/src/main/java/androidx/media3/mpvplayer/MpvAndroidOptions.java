package androidx.media3.mpvplayer;

import java.io.File;

/** 上游定制 media3 私有模块 MpvAndroidOptions（Android 平台 mpv 选项）。 */
public final class MpvAndroidOptions {

    public final File shaderCacheDirectory;
    public final boolean audioPassthroughEnabled;
    public final int dolbyVisionOutputPolicy;
    public final boolean gpuNextEnabled;
    public final boolean vulkanEnabled;

    private MpvAndroidOptions(Builder builder) {
        this.shaderCacheDirectory = builder.shaderCacheDirectory;
        this.audioPassthroughEnabled = builder.audioPassthroughEnabled;
        this.dolbyVisionOutputPolicy = builder.dolbyVisionOutputPolicy;
        this.gpuNextEnabled = builder.gpuNextEnabled;
        this.vulkanEnabled = builder.vulkanEnabled;
    }

    public static final class Builder {

        private File shaderCacheDirectory;
        private boolean audioPassthroughEnabled;
        private int dolbyVisionOutputPolicy;
        private boolean gpuNextEnabled;
        private boolean vulkanEnabled;

        public Builder() {
        }

        public Builder setShaderCacheDirectory(File dir) {
            this.shaderCacheDirectory = dir;
            return this;
        }

        public Builder setAudioPassthroughEnabled(boolean enabled) {
            this.audioPassthroughEnabled = enabled;
            return this;
        }

        public Builder setDolbyVisionOutputPolicy(int policy) {
            this.dolbyVisionOutputPolicy = policy;
            return this;
        }

        public Builder setGpuNextEnabled(boolean enabled) {
            this.gpuNextEnabled = enabled;
            return this;
        }

        public Builder setVulkanEnabled(boolean enabled) {
            this.vulkanEnabled = enabled;
            return this;
        }

        public MpvAndroidOptions build() {
            return new MpvAndroidOptions(this);
        }
    }
}
