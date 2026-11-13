package com.kjs.wuli3.http.logging;

import java.util.Map;

/**
 * 客户端初始化与请求共享的日志接收器，运行时异常不会中断 HTTP 链路。
 *
 * 注意：实现须支持并发调用并快速返回；使用 exchangeId 关联请求，自行写入的诊断字段需自行脱敏。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
@FunctionalInterface
public interface HttpLogRecorder {

    /** 接收类型明确的生命周期事件或诊断事件及其字段。 */
    void record(HttpLogEvent event, Map<String, ?> fields);
}
