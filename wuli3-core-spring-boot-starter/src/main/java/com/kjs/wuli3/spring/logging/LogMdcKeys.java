package com.kjs.wuli3.spring.logging;

/**
 * 默认日志格式中由 Wuli3 写入的 MDC 字段名契约。
 *
 * 注意：trace_id、span_id 由 OpenTelemetry Java Agent 写入，字段名契约由 OpenTelemetry starter 的 TraceMdc 声明，
 * 本类不声明也不写入这两个字段。
 *
 * @author lipt create on 2026/10/16 18:56
 */
public final class LogMdcKeys {

    /** 调用链请求标识。 */
    public static final String REQUEST_ID = "requestId";

    private LogMdcKeys() {}
}
