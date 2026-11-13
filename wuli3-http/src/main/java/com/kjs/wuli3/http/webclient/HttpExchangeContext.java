package com.kjs.wuli3.http.webclient;

import com.kjs.wuli3.http.logging.HttpLogRecorder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每次订阅独立的请求标识与回调共享数据；日志接收器在客户端内共享。
 *
 * 注意：回调可能切换线程；attributes 支持并发访问，其中存放的对象由调用方保证线程安全。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
public final class HttpExchangeContext {

    private final String id = UUID.randomUUID().toString();
    private final String clientName;
    private final HttpLogRecorder logRecorder;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    HttpExchangeContext(final String clientName, final HttpLogRecorder logRecorder) {
        this.clientName = clientName;
        this.logRecorder = logRecorder;
    }

    /** 返回本次订阅唯一的调用标识。 */
    public String id() {
        return this.id;
    }

    /** 返回配置中的客户端名称。 */
    public String clientName() {
        return this.clientName;
    }

    /** 返回客户端共享的日志接收器；使用 id() 关联本次调用的诊断字段。 */
    public HttpLogRecorder logRecorder() {
        return this.logRecorder;
    }

    /** 返回回调间共享的诊断与业务临时数据。 */
    public Map<String, Object> attributes() {
        return this.attributes;
    }
}
