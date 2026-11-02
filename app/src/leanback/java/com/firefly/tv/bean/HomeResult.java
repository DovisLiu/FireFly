package com.firefly.tv.bean;

/**
 * 首页 homeContent 结果的进程级缓存（TV 2.0 §2.1/§3.2）：
 * 供非首页列表页的导航"点播"入口与首页二级菜单直出读取。
 * HomeActivity 在 homeContent 回调时 put；切源时覆盖。
 */
public class HomeResult {

    private static volatile Result sResult;

    public static void put(Result result) {
        sResult = result;
    }

    public static Result get() {
        return sResult;
    }
}
