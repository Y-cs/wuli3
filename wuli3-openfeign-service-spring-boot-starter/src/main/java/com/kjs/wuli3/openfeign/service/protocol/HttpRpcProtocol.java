package com.kjs.wuli3.openfeign.service.protocol;

/**
 * 服务端内部 HTTP RPC 的版本与媒体类型契约。
 * @author 国杨 create on 2026/11/5 15:00
 */
public final class HttpRpcProtocol {
    public static final String VERSION_HEADER = "X-Wuli3-Rpc-Version";
    public static final String VERSION = "1";
    public static final String ERROR_CONTENT_TYPE = "application/vnd.wuli3.rpc-error+json";
    public static final String DEFAULT_PATH = "/internal/rpc";
    /** 上下文字段编码后的最大传输字符数。 */
    public static final int MAX_HEADER_LENGTH = 4096;
    /** 错误文档最大字节数。 */
    public static final int MAX_ERROR_BYTES = 65536;

    private HttpRpcProtocol() {}
}
