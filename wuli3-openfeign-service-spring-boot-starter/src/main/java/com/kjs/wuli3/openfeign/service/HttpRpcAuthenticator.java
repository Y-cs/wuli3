package com.kjs.wuli3.openfeign.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 在恢复用户上下文之前验证直接调用方的服务身份。
 *
 * 注意：由应用接入工作负载身份、mTLS 或已有认证机制，不得仅凭传播头或接口路径信任调用方。
 *
 * @author 国杨 create on 2026/11/5 10:00
 */
@FunctionalInterface
public interface HttpRpcAuthenticator {

    /** 验证服务身份；返回 false 时拒绝调用，且不恢复调用方提交的上下文。 */
    boolean authenticate(HttpServletRequest request);
}
