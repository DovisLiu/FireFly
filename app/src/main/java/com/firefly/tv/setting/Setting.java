package com.firefly.tv.setting;

import com.firefly.tv.R;
import com.firefly.tv.utils.Formatters;
import com.firefly.tv.utils.ResUtil;
import com.github.catvod.utils.Prefers;

import java.time.LocalDate;

public class Setting {

    private static final int MIN_SITE_MODE = 0;
    private static final int MAX_SITE_MODE = 1;
    private static final int MIN_SYNC_MODE = 0;
    private static final int MAX_SYNC_MODE = 2;

    public static String getSwitch(boolean value) {
        return ResUtil.getString(value ? R.string.setting_on : R.string.setting_off);
    }

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    // 心跳上报标识：uuid 首启随机生成终身跟随；lastPing 用于 24 小时频控
    public static String getUuid() {
        return Prefers.getString("report_uuid");
    }

    public static void putUuid(String uuid) {
        Prefers.put("report_uuid", uuid);
    }

    public static long getLastPing() {
        return Prefers.getLong("report_last_ping", 0L);
    }

    public static void putLastPing(long time) {
        Prefers.put("report_last_ping", time);
    }

    // 外观模式：0 跟随系统 / 1 浅色 / 2 深色
    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    public static int getThemeMode() {
        return Math.clamp(Prefers.getInt("theme_mode", MODE_SYSTEM), MODE_SYSTEM, MODE_DARK);
    }

    public static void putThemeMode(int mode) {
        Prefers.put("theme_mode", Math.clamp(mode, MODE_SYSTEM, MODE_DARK));
    }

    public static int getSiteMode() {
        return Math.clamp(Prefers.getInt("site_mode"), MIN_SITE_MODE, MAX_SITE_MODE);
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", Math.clamp(mode, MIN_SITE_MODE, MAX_SITE_MODE));
    }

    public static int getSyncMode() {
        return Math.clamp(Prefers.getInt("sync_mode"), MIN_SYNC_MODE, MAX_SYNC_MODE);
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", Math.clamp(mode, MIN_SYNC_MODE, MAX_SYNC_MODE));
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }


    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }

    // TV 搜索键盘：拼音选字模式（字母累积出汉字候选）与直接输入模式切换
    public static boolean isKbPinyin() {
        return Prefers.getBoolean("kbPinyin");
    }

    public static void putKbPinyin(boolean enabled) {
        Prefers.put("kbPinyin", enabled);
    }

    // 迁移公告：最后已知阶段（拉取成功即持久化，断网兜底）
    public static int getNoticeStage() {
        return Prefers.getInt("noticeStage", 0);
    }

    public static void putNoticeStage(int stage) {
        Prefers.put("noticeStage", stage);
    }

    // 迁移公告：提示阶段（stage=1）当日是否已弹过
    public static boolean isNoticeShownToday() {
        return Prefers.getString("noticeDay").equals(LocalDate.now().format(Formatters.DATE));
    }

    public static void putNoticeShownToday() {
        Prefers.put("noticeDay", LocalDate.now().format(Formatters.DATE));
    }

    // 迁移公告：首次安装弹窗（stage=4）是否已弹过（弹过永久记住，不再弹）
    public static boolean isNoticeFirstShown() {
        return Prefers.getBoolean("noticeFirstShown");
    }

    public static void putNoticeFirstShown() {
        Prefers.put("noticeFirstShown", true);
    }
}
