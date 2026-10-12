package androidx.media3.mpvplayer.audio;

/** 上游定制 media3 私有模块的空实现（stub），静态方法返回单位矩阵。 */
public final class AudioChannelMix {

    private AudioChannelMix() {
    }

    private static float[][] identity(int channelCount) {
        if (channelCount <= 0) return new float[0][0];
        float[][] m = new float[channelCount][channelCount];
        for (int i = 0; i < channelCount; i++) m[i][i] = 1.0f;
        return m;
    }

    public static float[][] createFrontCenterGainMix(int channelCount, float gain) {
        return identity(channelCount);
    }

    public static float[][] createStereoMix(int channelCount, boolean reverse) {
        return identity(channelCount);
    }

    public static float[][] createMonoMix(int channelCount) {
        return identity(channelCount);
    }

    public static float[][] createFrontBalanceMix(int channelCount, float balance) {
        return identity(channelCount);
    }

    public static float[][] compose(float[][] a, float[][] b) {
        return a;
    }

    public static float mixStereoLeft(float[] samples) {
        return 0f;
    }

    public static float mixStereoRight(float[] samples) {
        return 0f;
    }

    public static float mixMono(float[] samples) {
        return 0f;
    }
}
