package com.firefly.tv.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.firefly.tv.BuildConfig;
import com.firefly.tv.setting.Setting;
import com.github.catvod.net.OkHttp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * 心跳上报：冷启动 + 每 24 小时各一次，向自有服务器（download.dovisliu.cn/firefly/ping）
 * 报送匿名设备标识，仅用于使用统计、通知定下与异常排查。服务器只回 204，无任何业务。
 *
 * 采集红线：只收 uuid（随机生成）+ aid（ANDROID_ID 单向哈希）+ 机型/版本/端。
 * 绝不采集 IMEI、序列号、MAC、电话号码；IP 仅存在于服务器访问日志中用于排查。
 *
 * 所有异常静默吞掉——统计链路绝不能影响 App 正常使用。
 */
public class Report {

    private static final String PING = "https://download.dovisliu.cn/firefly/ping";
    private static final long INTERVAL = 24 * 60 * 60 * 1000L;

    public static void ping() {
        try {
            long now = System.currentTimeMillis();
            if (now - Setting.getLastPing() < INTERVAL) return;
            String query = "uuid=" + getUuid()
                    + "&aid=" + sha1(Util.getAndroidId())
                    + "&model=" + Uri.encode(Util.getDeviceName())
                    + "&ver=" + Uri.encode(BuildConfig.VERSION_NAME)
                    + "&mode=" + ("leanback".equals(BuildConfig.FLAVOR_mode) ? "tv" : "phone");
            OkHttp.string(PING + "?" + query);
            Setting.putLastPing(now);
        } catch (Exception e) {
            // 静默：心跳失败无感
        }
    }

    private static String getUuid() {
        String uuid = Setting.getUuid();
        if (TextUtils.isEmpty(uuid)) {
            uuid = UUID.randomUUID().toString();
            Setting.putUuid(uuid);
        }
        return uuid;
    }

    // ANDROID_ID 哈希后上报：分析只需"值是否相同"，无需还原原值，泄露面更小。
    // 返回全 0（部分老盒子的回退值）时上报空串，服务端退回按 uuid 判重。
    private static String sha1(String text) {
        try {
            if (TextUtils.isEmpty(text) || "0000000000000000".equals(text)) return "";
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.substring(0, 16);
        } catch (Exception e) {
            return "";
        }
    }
}
