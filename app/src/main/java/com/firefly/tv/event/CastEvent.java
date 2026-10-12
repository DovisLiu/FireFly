package com.firefly.tv.event;

import com.firefly.tv.bean.Config;
import com.firefly.tv.bean.Device;
import com.firefly.tv.bean.History;

import org.greenrobot.eventbus.EventBus;

public record CastEvent(Config config, Device device, History history) {

    public static void post(Config config, Device device, History history) {
        EventBus.getDefault().post(new CastEvent(config, device, history));
    }
}
