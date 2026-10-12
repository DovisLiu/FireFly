package com.firefly.tv.utils;

import com.firefly.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import okhttp3.Response;

public class Download {

    private final File file;
    private final String url;
    private Callback callback;
    private Future<?> future;
    private long maxBytes;
    private String tag;

    public static Download create(String url, File file) {
        return new Download(url, file);
    }

    public Download(String url, File file) {
        this.maxBytes = Long.MAX_VALUE;
        this.tag = url;
        this.url = url;
        this.file = file;
    }

    public Download tag(String tag) {
        this.tag = tag;
        return this;
    }

    public Download maxBytes(long maxBytes) {
        this.maxBytes = maxBytes > 0 ? maxBytes : Long.MAX_VALUE;
        return this;
    }

    public File get() {
        doInBackground();
        return file;
    }

    public void start(Callback callback) {
        this.callback = callback;
        future = Task.submit(this::doInBackground);
    }

    public Download cancel() {
        if (future != null) future.cancel(true);
        OkHttp.cancel(tag);
        future = null;
        return this;
    }

    // 断点续传：已有部分文件时带 Range 续传（206 追加 / 200 重来），中断不再删除缓存文件
    private void doInBackground() {
        try {
            long existing = file.exists() ? file.length() : 0;
            Map<String, String> headers = new HashMap<>();
            if (existing > 0) headers.put(HttpHeaders.RANGE, "bytes=" + existing + "-");
            try (Response res = OkHttp.newCall(url, tag, headers).execute()) {
                boolean resume = existing > 0 && res.code() == 206;
                long base = resume ? existing : 0;
                if (!resume) Path.clear(file);
                double total = base + getLength(res);
                if (!download(res.body().byteStream(), total, base, resume)) return; // 被取消中断：不弹安装
                if (callback != null) App.post(() -> callback.success(file));
            }
        } catch (Exception e) {
            if (callback != null) App.post(() -> callback.error(e.getMessage()));
            else throw new RuntimeException(e.getMessage(), e);
        }
    }

    // 返回 false = 被取消中断（保留续传文件，不触发安装）
    private boolean download(InputStream is, double total, long base, boolean resume) throws IOException {
        if (total > maxBytes) throw new IOException("Download size limit exceeded");
        try (BufferedInputStream input = new BufferedInputStream(is); FileOutputStream os = new FileOutputStream(Path.create(file), resume)) {
            byte[] buffer = new byte[16384];
            int readBytes;
            long written = base;
            while ((readBytes = input.read(buffer)) != -1) {
                if (Thread.interrupted()) return false;
                written += readBytes;
                if (total > 0 && written > maxBytes) throw new IOException("Download size limit exceeded");
                os.write(buffer, 0, readBytes);
                if (total <= 0) continue;
                int progress = (int) (written / total * 100.0);
                if (callback != null) App.post(() -> callback.progress(progress));
            }
        }
        return true;
    }

    private double getLength(Response res) {
        try {
            String header = res.header(HttpHeaders.CONTENT_LENGTH);
            return header != null ? Double.parseDouble(header) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public interface Callback {

        void progress(int progress);

        void error(String msg);

        void success(File file);
    }
}
