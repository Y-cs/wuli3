package com.kjs.wuli3.propagation.internal;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.propagation.codec.ContextCodec;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.context.PropagationContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 业务代码可读取的认证与授权元数据。
 *
 * @param principalType 认证主体类型
 * @param principalId 认证主体的唯一标识
 * @param principalName 认证主体的显示名称
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public record AuthContext(PrincipalType principalType, String principalId, String principalName)
        implements PropagationContext {

    public static final String PRINCIPAL_TYPE = "X-Principal-Type";
    public static final String PRINCIPAL_ID = "X-Principal-Id";
    public static final String PRINCIPAL_NAME = "X-Principal-Name";

    private static final ContextCodec<AuthContext> CODEC = new Codec();

    /** 创建字段完整的认证主体快照。 */
    public AuthContext {
        Asserts.whenNull(principalType)
                .throwIllegalArgumentException("principalType must not be null");
        AuthContext.requireNonBlank(principalId, "principalId");
        AuthContext.requireNonBlank(principalName, "principalName");
    }

    /**
     * 返回认证上下文的类型，用作上下文容器中的存取键。
     *
     * @return {@link AuthContext} 的类型
     */
    @Override
    public ContextKey<AuthContext> contentKey() {
        return ContextKey.of(AuthContext.class);
    }

    @Override
    public ContextCodec<AuthContext> contentCodec() {
        return CODEC;
    }

    private static void requireNonBlank(final String value, final String name) {
        Asserts.whenNull(value)
                .throwIllegalArgumentException(name + " must not be null");
        Asserts.whenBlank(value)
                .throwIllegalArgumentException(name + " must not be blank");
    }

    public static final class Codec implements ContextCodec<AuthContext> {
        @Override
        public ContextKey<AuthContext> contentKey() {
            return ContextKey.of(AuthContext.class);
        }

        @Override
        public Set<String> fieldNames() {
            return Set.of(AuthContext.PRINCIPAL_TYPE, AuthContext.PRINCIPAL_ID, AuthContext.PRINCIPAL_NAME);
        }

        @Override
        public void encode(AuthContext context, BiConsumer<String, String> fieldWriter) {
            final AuthContext actualContext = Objects.requireNonNull(context, "context");
            final BiConsumer<String, String> actualFieldWriter = Objects.requireNonNull(fieldWriter, "fieldWriter");
            actualFieldWriter.accept(
                    AuthContext.PRINCIPAL_TYPE, actualContext.principalType().name());
            actualFieldWriter.accept(AuthContext.PRINCIPAL_ID, actualContext.principalId());
            actualFieldWriter.accept(AuthContext.PRINCIPAL_NAME, actualContext.principalName());
        }

        @SuppressWarnings("NullAway")
        @Override
        public Optional<AuthContext> decode(Function<String, @Nullable String> fieldReader) {
            Objects.requireNonNull(fieldReader, "fieldReader");
            final String principalTypeStr = fieldReader.apply(AuthContext.PRINCIPAL_TYPE);
            final String principalId = fieldReader.apply(AuthContext.PRINCIPAL_ID);
            final String principalName = fieldReader.apply(AuthContext.PRINCIPAL_NAME);
            if (principalTypeStr == null || principalId == null || principalName == null) {
                return Optional.empty();
            }
            final PrincipalType principalType = PrincipalType.parse(principalTypeStr);
            return Optional.of(new AuthContext(principalType, principalId, principalName));
        }
    }

}
