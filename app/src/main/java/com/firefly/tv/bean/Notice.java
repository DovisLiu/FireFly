package com.firefly.tv.bean;

import android.text.TextUtils;

import org.json.JSONObject;

public class Notice {

    private int stage;
    private String title;
    private String message;
    private String buttonText;
    private String cancelText;
    private String flowUrl;
    private String flowApkUrl;
    private String flowMobileUrl;

    public Notice() {
    }

    // 拉取失败但有阶段缓存时的兜底构造：文案为空，弹窗用内置文案
    public Notice(int stage) {
        this.stage = stage;
    }

    // stage: 0=静默 1=提示可跳过(每日一次) 2=强制下载提示(每次启动,仍可用) 3=停止服务(不可关闭) 4=首次安装弹窗(只弹一次)
    public static Notice objectFrom(String json) {
        try {
            JSONObject object = new JSONObject(json);
            Notice notice = new Notice();
            notice.stage = object.optInt("stage", 0);
            notice.title = object.optString("title");
            notice.message = object.optString("message");
            notice.buttonText = object.optString("buttonText");
            notice.cancelText = object.optString("cancelText");
            notice.flowUrl = object.optString("flowUrl");
            notice.flowApkUrl = object.optString("flowApkUrl");
            notice.flowMobileUrl = object.optString("flowMobileUrl");
            return notice;
        } catch (Exception e) {
            return null;
        }
    }

    public int getStage() {
        return stage;
    }

    public String getTitle() {
        return TextUtils.isEmpty(title) ? "" : title;
    }

    public String getMessage() {
        return TextUtils.isEmpty(message) ? "" : message;
    }

    // 主按钮文案由 JSON 下发（新应用叫什么名由服务器决定），空则用内置「确定」
    public String getButtonText() {
        return TextUtils.isEmpty(buttonText) ? "" : buttonText;
    }

    // 次按钮文案同理，空则用内置「取消」
    public String getCancelText() {
        return TextUtils.isEmpty(cancelText) ? "" : cancelText;
    }

    public String getFlowUrl() {
        return flowUrl == null ? "" : flowUrl;
    }

    public String getFlowApkUrl() {
        return flowApkUrl == null ? "" : flowApkUrl;
    }

    public String getFlowMobileUrl() {
        return flowMobileUrl == null ? "" : flowMobileUrl;
    }
}
