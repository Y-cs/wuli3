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
     * <p>默认实现优先使用异常明确声明的责任与协议状态，候选错误码仅用于未声明状态时的回退。
     * 尚未归类的框架异常可传入 {@link WebErrors#INTERNAL_ERROR}，该占位码不覆盖异常自身的 HTTP 语义。
     * 非标准 4xx 默认回退为 400，其他非标准或非错误状态回退为 500。
     */
    HttpStatus resolve(Throwable error, ErrorCode responseCode);
}
