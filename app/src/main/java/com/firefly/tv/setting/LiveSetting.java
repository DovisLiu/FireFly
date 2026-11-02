package com.firefly.tv.setting;

import com.github.catvod.utils.Prefers;

public class LiveSetting {

    public static boolean isBoot() {
        return Prefers.getBoolean("boot_live");
    }

    public static void putBoot(boolean boot) {
        Prefers.put("boot_live", boot);
    }

    public static boolean isAcross() {
        return Prefers.getBoolean("across", true);
    }

    public static void putAcross(boolean across) {
        Prefers.put("across", across);
    }

    public static boolean isChange() {
        return Prefers.getBoolean("change", true);
    }

    public static void putChange(boolean change) {
        Prefers.put("change", change);
    }

    public static boolean isInvert() {
        return Prefers.getBoolean("invert");
    }

    public static void putInvert(boolean invert) {
        Prefers.put("invert", invert);
    }

    /** 直播选台/换台面板预览开关（spec §3.5：低配设备可关） */
    public static boolean isPreview() {
        return Prefers.getBoolean("live_preview", true);
    }

    public static void putPreview(boolean preview) {
        Prefers.put("live_preview", preview);
    }

    public static int getScale() {
        return Math.clamp(Prefers.getInt("scale_live", PlayerSetting.getScale()), PlayerSetting.MIN_SCALE, PlayerSetting.MAX_SCALE);
    }

    public static void putScale(int scale) {
        Prefers.put("scale_live", Math.clamp(scale, PlayerSetting.MIN_SCALE, PlayerSetting.MAX_SCALE));
    }
}
