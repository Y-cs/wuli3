package com.kjs.wuli3.openfeign.service.context;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.openfeign.service.protocol.HttpRpcProtocol;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.context.ContextSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 将已认证内部请求的有界单值传播头还原为上下文快照。
 * <p>
 * 注意：本类只读取和校验，不安装线程状态；服务身份认证必须由 Servlet 在调用前完成。
 *
 * @author 国杨 create on 2026/11/5 16:00
 */
public final class HttpRpcRequestContext {

    private final ContextPropagator propagator;

    /** 使用共享传播器读取显式启用的上下文字段；该类型供 Servlet 装配，不是业务扩展点。 */
    public HttpRpcRequestContext(final ContextPropagator propagator) {
        this.propagator = Objects.requireNonNull(propagator, "propagator");
    }

    /**
     * 校验保留头后读取快照，缺失或不支持的版本返回空，交给入口输出协议参数错误。
     * <p>
     * 注意：先校验所有保留头，再判断版本，避免不支持的版本跳过重复值、超限或编码检查。
     * 本类不补入当前线程的上下文，快照只反映本次请求中传播器允许接收的字段。
     *
     * @return 版本无效时返回 Optional.empty；版本有效但没有上下文字段时返回包含空快照的 Optional
     * @throws com.kjs.wuli3.core.error.ErrorCodeException 传播头重复、编码损坏或超限
     */
    public Optional<ContextSnapshot> read(final HttpServletRequest request) {
        final Map<String, String> fields = this.fields(request);
        if (!HttpRpcProtocol.VERSION.equals(fields.get(HttpRpcProtocol.VERSION_HEADER.toLowerCase(Locale.ROOT)))) {
            return Optional.empty();
        }
        return Optional.of(this.snapshot(fields));
    }

    /** 缺失字段传 null；NullAway 尚不能识别跨模块的可空 Function 返回值。 */
    @SuppressWarnings("NullAway")
    private ContextSnapshot snapshot(final Map<String, String> fields) {
        return this.propagator.extract(name -> fields.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * 统一校验传播器识别的保留头，拒绝重复、超限及编码歧义；普通业务头不参与上下文提取。
     * 版本头保留明文，其余字段严格解码后按小写名称保存，供传播器执行字段级提取。
     */
    private Map<String, String> fields(final HttpServletRequest request) {
        final Map<String, String> fields = new HashMap<>();
        final Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            final String name = names.nextElement();
            if (!this.propagator.isReservedFieldName(name)) {
                continue;
            }
            final Enumeration<String> values = request.getHeaders(name);
            if (!values.hasMoreElements()) {
                continue;
            }
            final String value = values.nextElement();
            Asserts.whenTrue(values.hasMoreElements())
                    .throwIllegalArgumentException("duplicate internal context header");
            Asserts.whenTrue(value.length() > HttpRpcProtocol.MAX_HEADER_LENGTH
                            || value.indexOf('\r') >= 0
                            || value.indexOf('\n') >= 0)
                    .throwIllegalArgumentException("invalid internal context header");
            final String decoded;
            if (HttpRpcProtocol.VERSION_HEADER.equalsIgnoreCase(name)) {
                decoded = value;
            } else {
                final Optional<String> parsed = HttpRpcRequestContext.decode(value);
                Asserts.whenTrue(parsed.isEmpty()).throwIllegalArgumentException("invalid internal context encoding");
                decoded = parsed.orElseThrow();
            }
            final String previous = fields.put(name.toLowerCase(Locale.ROOT), decoded);
            Asserts.whenTrue(previous != null).throwIllegalArgumentException("duplicate internal context field");
        }
        return fields;
    }

    /** 严格拒绝非规范 Base64URL、非法 UTF-8 和控制字符，不猜测损坏的身份字段。 */
    private static Optional<String> decode(final String value) {
        try {
            final byte[] bytes = Base64.getUrlDecoder().decode(value);
            final String decoded = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value)
                    || decoded.chars().anyMatch(character -> character < 32 || character == 127)) {
                return Optional.empty();
            }
            return Optional.of(decoded);
        } catch (final IllegalArgumentException | CharacterCodingException malformed) {
            return Optional.empty();
        }
    }
}
