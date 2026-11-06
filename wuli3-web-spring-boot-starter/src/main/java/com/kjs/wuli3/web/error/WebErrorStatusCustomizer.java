package com.kjs.wuli3.web.error;

import org.springframework.http.HttpStatus;

/**
 * 根据已识别的 Web 错误上下文定制 HTTP 状态。
 *
 * 注意：这是可选的应用扩展，默认识别完成后仅调用一次；无需定制时返回上下文中的默认状态。
 * 此扩展不改变业务错误码、责任、严重度、可见性或校验详情公开资格。
 * 状态变化时协议错误码随之更新，原协议头被清除；非错误状态归一化为 500。
 *
 * @author 国杨 create on 2026/11/5 17:10
 */
@FunctionalInterface
public interface WebErrorStatusCustomizer {

    /** 返回最终使用的 HTTP 错误状态，保留默认行为时返回 {@code context.defaultStatus()}。 */
    HttpStatus customize(WebErrorStatusContext context);
}
