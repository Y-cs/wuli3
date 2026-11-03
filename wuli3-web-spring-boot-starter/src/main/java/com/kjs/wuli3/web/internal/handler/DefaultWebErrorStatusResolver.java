package com.kjs.wuli3.web.internal.handler;

import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.web.error.WebErrorStatusResolver;
import org.springframework.http.HttpStatus;

/**
 * 将内部完整错误决策适配为既有 HTTP 状态解析 SPI。
 *
 * 注意：默认 MVC 出口直接复用完整决策，不通过此适配器重复识别。
 * 直接调用此 SPI 时，只有未识别异常才使用候选错误码回退。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public final class DefaultWebErrorStatusResolver implements WebErrorStatusResolver {

    /** 返回已识别状态；未识别时采用候选协议码，其他情况回退为 500。 */
    @Override
    public HttpStatus resolve(final Throwable error, final ErrorCode responseCode) {
        final WebErrorResolution resolution = WebErrorResolver.find(error);
        if (resolution != null) {
            return resolution.status();
        }
        final HttpStatus candidate = WebErrorResolver.webStatus(responseCode);
        return candidate == null ? HttpStatus.INTERNAL_SERVER_ERROR : candidate;
    }
}
