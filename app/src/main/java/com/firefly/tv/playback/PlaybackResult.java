package com.firefly.tv.playback;

import com.firefly.tv.bean.Result;

public record PlaybackResult<T>(T request, Result result) {
}
