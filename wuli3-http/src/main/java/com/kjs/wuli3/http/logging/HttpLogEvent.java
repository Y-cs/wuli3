package com.kjs.wuli3.http.logging;

/**
 * HTTP 日志事件类型，统一初始化、请求生命周期与回调诊断的事件标识。
 *
 * @author 国杨 create on 2026/11/13 17:00
 */
public enum HttpLogEvent {
    /** 客户端构建完成。 */
    INITIALIZED,
    /** 客户端初始化或构建失败。 */
    INITIALIZATION_ERROR,
    /** 请求已完成前置处理，准备发送。 */
    REQUEST,
    /** 已收到响应头。 */
    RESPONSE,
    /** HTTP 调用因完成、错误或取消而终结。 */
    COMPLETE,
    /** 调用方在生命周期回调中补充的诊断事件。 */
    DIAGNOSTIC
}
