package com.wulicode.wuli3.http.logging;

/**
 * HTTP 日志事件类型，区分客户端初始化与单次请求的终结汇总。
 *
 * @author 国杨 create on 2026/11/13 17:00
 */
public enum HttpLogEvent {
    /** 客户端构建完成。 */
    INITIALIZED,
    /** 客户端初始化或构建失败。 */
    INITIALIZATION_ERROR,
    /** HTTP 调用因完成、错误或取消而终结。 */
    COMPLETE
}
