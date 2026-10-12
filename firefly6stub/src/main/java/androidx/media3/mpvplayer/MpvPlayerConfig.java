package androidx.media3.mpvplayer;

import android.content.Context;

import java.io.File;

/** 上游定制 media3 私有模块 MpvPlayerConfig（mpv 启动配置）。 */
public final class MpvPlayerConfig {

    public final File configDirectory;
    public final MpvAndroidOptions androidOptions;

    private MpvPlayerConfig(Builder builder) {
        this.configDirectory = builder.configDirectory;
        this.androidOptions = builder.androidOptions;
    }

    public static final class Builder {

        private File configDirectory;
        private MpvAndroidOptions androidOptions;

        public Builder() {
        }

        public Builder setHlsHttpPersistent(boolean value) {
            return this;
        }

        public Builder addConfigDirectory(File dir) {
            this.configDirectory = dir;
            return this;
        }

        public Builder addAndroidFontConfig(File configDir, File cacheDir) {
            return this;
        }

        public Builder addAndroidDefaults(MpvAndroidOptions options) {
            this.androidOptions = options;
            return this;
        }

        public Builder addTlsCaFileFromAsset(Context context, String assetName, File destFile) {
            return this;
        }

        public Builder addAndroidSubtitleOptions(Context context, MpvSubtitleOptions options) {
            return this;
        }

        public Builder addDiskCacheOptions(File dir, int seconds) {
            return this;
        }

        public MpvPlayerConfig build() {
            return new MpvPlayerConfig(this);
        }
    }
}
