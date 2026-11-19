package com.wulicode.wuli3.http.logging;

import java.util.Map;

/**
 * 客户端初始化与请求共享的日志接收器，运行时异常不会中断 HTTP 链路。
 *
 * 注意：实现须支持并发调用并快速返回；每次请求只接收一条 COMPLETE，业务诊断位于 diagnostics 字段。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
@FunctionalInterface
public interface HttpLogRecorder {

    /** 接收客户端初始化事件或请求终结汇总及其不可变字段。 */
    void record(HttpLogEvent event, Map<String, ?> fields);
}
