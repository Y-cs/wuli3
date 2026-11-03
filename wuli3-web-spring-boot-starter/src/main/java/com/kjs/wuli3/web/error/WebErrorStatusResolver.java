package com.kjs.wuli3.web.error;

import com.kjs.wuli3.core.error.model.ErrorCode;
import org.springframework.http.HttpStatus;

/** 解析已处理 Web 错误应返回的 HTTP 状态。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@FunctionalInterface
public interface WebErrorStatusResolver {

    /**
     * 根据当前异常和候选错误码返回 HTTP 状态。
     *
     * <p>默认出口一次识别错误及协议信息；应用提供此 SPI 时，在识别后调用一次以显式覆盖状态。
     * 此扩展不改变业务错误的责任、严重度或可见性；状态改变后不复用原状态的协议头。
     * 本地 Web 协议错误保留与责任一致的具体 4xx 或 5xx；责任覆盖后若系列不一致，则按新责任回退为 400 或 500。
     * 非标准 4xx 默认回退为 400，其他非标准或非错误状态回退为 500。
     *
     * @param error 显式映射后的异常；未映射时为原始异常
     * @param responseCode 已识别的业务码或协议码，不使用未分类占位码；直接调用默认实现时仍兼容候选码回退
     */
    HttpStatus resolve(Throwable error, ErrorCode responseCode);
}
