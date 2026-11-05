package com.kjs.wuli3.openfeign.service.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.openfeign.service.protocol.HttpRpcProtocol;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 验证请求头校验与快照提取组成同一边界，保留严格字符编码和版本错误的语义。
 *
 * @author 国杨 create on 2026/11/5 16:00
 */
class HttpRpcRequestContextTest {

    private final HttpRpcRequestContext reader =
            new HttpRpcRequestContext(new ContextPropagator(ContextPropagator.standardContextEncoder()));

    /** 字段名忽略大小写，Unicode 内容在快照中完整保留。 */
    @Test
    void readsUnicodeInvocationAndAcceptsEmptySnapshot() {
        final MockHttpServletRequest request = this.request();
        assertThat(this.reader.read(request).orElseThrow().isEmpty()).isTrue();
        request.addHeader("x-wuli3-request-id", HttpRpcRequestContextTest.encode("用户-42"));
        request.addHeader(InvocationContext.ORIGIN_IP, HttpRpcRequestContextTest.encode("198.51.100.8"));
        assertThat(this.reader.read(request).orElseThrow().get(ContextKey.of(InvocationContext.class)))
                .contains(new InvocationContext("198.51.100.8", "用户-42"));
    }

    /** 损坏、超限或含控制字符的头必须在统一错误链中失败。 */
    @Test
    void rejectsMalformedContextHeaders() {
        for (final String encoded : List.of(
                "not valid encoding",
                "YQ==",
                "a".repeat(HttpRpcProtocol.MAX_HEADER_LENGTH + 1),
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {(byte) 255}),
                HttpRpcRequestContextTest.encode("line\nfeed"))) {
            final MockHttpServletRequest request = this.request();
            request.addHeader(InvocationContext.REQUEST_ID, encoded);
            assertThatThrownBy(() -> this.reader.read(request)).isInstanceOf(ErrorCodeException.class);
        }
        final MockHttpServletRequest duplicate = this.request();
        duplicate.addHeader(InvocationContext.REQUEST_ID, HttpRpcRequestContextTest.encode("first"));
        duplicate.addHeader(InvocationContext.REQUEST_ID, HttpRpcRequestContextTest.encode("second"));
        assertThatThrownBy(() -> this.reader.read(duplicate)).isInstanceOf(ErrorCodeException.class);
    }

    /** 先校验全部保留头，再判断版本；混合非法输入不得被版本错误掩盖。 */
    @Test
    void validatesHeadersBeforeReturningUnsupportedVersion() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(this.reader.read(request)).isEmpty();
        request.addHeader(HttpRpcProtocol.VERSION_HEADER, "2");
        assertThat(this.reader.read(request)).isEmpty();
        request.addHeader(InvocationContext.REQUEST_ID, "bad encoding");
        assertThatThrownBy(() -> this.reader.read(request)).isInstanceOf(ErrorCodeException.class);
    }

    /** 创建合法版本的请求，不隐式填充调用者上下文。 */
    private MockHttpServletRequest request() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpRpcProtocol.VERSION_HEADER, HttpRpcProtocol.VERSION);
        return request;
    }

    /** 构造独立的 HTTP 字段样本，不依赖提供端的私有实现。 */
    private static String encode(final String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
