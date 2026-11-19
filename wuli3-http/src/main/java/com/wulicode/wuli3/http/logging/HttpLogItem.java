package com.wulicode.wuli3.http.logging;

/**
 * 选择单次 HTTP 汇总日志包含的详情；基础摘要始终保留。
 *
 * @author 国杨 create on 2026/11/16 11:33
 */
public enum HttpLogItem {
    /** 请求处理后的头部。 */
    REQUEST_HEADERS,
    /** 响应处理后的头部。 */
    RESPONSE_HEADERS,
    /** 编码发送时的请求正文。 */
    REQUEST_BODY,
    /** 响应处理后、随调用方消费的正文。 */
    RESPONSE_BODY
}
