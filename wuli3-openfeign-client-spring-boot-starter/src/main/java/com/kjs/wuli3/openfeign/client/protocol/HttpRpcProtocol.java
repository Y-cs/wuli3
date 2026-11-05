package com.kjs.wuli3.openfeign.client.protocol;

import org.jspecify.annotations.NullMarked;

/**
 * 客户端内部 HTTP RPC 的版本、媒体类型与传输大小契约。
 *
 * 注意：客户端与服务端各自维护线协议常量，通过跨端测试保证一致，不引入运行时模块依赖。
 * 本类供客户端实现装配，不是应用扩展点。
 *
 * @author 国杨 create on 2026/11/5 15:00
 */
@NullMarked
public final class HttpRpcProtocol {
    /** 请求及错误响应中的明文协议版本头。 */
    public static final String VERSION_HEADER = "X-Wuli3-Rpc-Version";
    /** 当前只接受完全匹配的协议版本。 */
    public static final String VERSION = "1";
    /** 与公开 Web 错误响应区分的内部错误媒体类型。 */
    public static final String ERROR_CONTENT_TYPE = "application/vnd.wuli3.rpc-error+json";
    /** 上下文字段编码后的最大传输字符数。 */
    public static final int MAX_HEADER_LENGTH = 4096;
    /** 错误文档最大字节数，读取额外一个字节以识别超限。 */
    public static final int MAX_ERROR_BYTES = 65536;

    private HttpRpcProtocol() {}
}
