package com.kjs.wuli3.propagation.internal;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.propagation.codec.ContextCodec;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.context.PropagationContext;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * 业务代码可读取的认证与授权元数据。
 *
 * @param principalType 认证主体类型
 * @param principalId 认证主体的唯一标识
 * @param principalName 认证主体的显示名称
 * @param principalPermissionToken 可信入口提供的可轮换权限主体码；缺失时不具备权限鉴权凭据
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public record AuthContext(
        PrincipalType principalType,
        String principalId,
        String principalName,
        @Nullable String principalPermissionToken)
        implements PropagationContext {

    public static final String PRINCIPAL_TYPE = "X-Wuli3-Principal-Type";
    public static final String PRINCIPAL_ID = "X-Wuli3-Principal-Id";
    public static final String PRINCIPAL_NAME = "X-Wuli3-Principal-Name";

    public static final String PRINCIPAL_PERMISSION_TOKEN = "X-Wuli3-Principal-Permission-Token";

    private static final ContextCodec<AuthContext> CODEC = new Codec();

    /** 创建字段完整的认证主体快照。 */
    public AuthContext {
        Asserts.whenNull(principalType).throwIllegalArgumentException("principalType must not be null");
        AuthContext.requireNonBlank(principalId, "principalId");
        AuthContext.requireNonBlank(principalName, "principalName");
        if (principalPermissionToken != null) {
            AuthContext.requireNonBlank(principalPermissionToken, "principalPermissionToken");
        }
    }

    /** 创建不携带权限鉴权凭据的认证上下文。 */
    public AuthContext(final PrincipalType principalType, final String principalId, final String principalName) {
        this(principalType, principalId, principalName, null);
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
        return AuthContext.CODEC;
    }

    private static void requireNonBlank(final String value, final String name) {
        Asserts.whenNull(value).throwIllegalArgumentException(name + " must not be null");
        Asserts.whenBlank(value).throwIllegalArgumentException(name + " must not be blank");
    }

    public static final class Codec implements ContextCodec<AuthContext> {
        @Override
        public ContextKey<AuthContext> contentKey() {
            return ContextKey.of(AuthContext.class);
        }

        @Override
        public Set<String> fieldNames() {
            return Set.of(
                    AuthContext.PRINCIPAL_TYPE,
                    AuthContext.PRINCIPAL_ID,
                    AuthContext.PRINCIPAL_NAME,
                    AuthContext.PRINCIPAL_PERMISSION_TOKEN);
        }

        @Override
        public void encode(final AuthContext context, final BiConsumer<String, String> fieldWriter) {
            final AuthContext actualContext = Objects.requireNonNull(context, "context");
            final BiConsumer<String, String> actualFieldWriter = Objects.requireNonNull(fieldWriter, "fieldWriter");
            actualFieldWriter.accept(
                    AuthContext.PRINCIPAL_TYPE, actualContext.principalType().name());
            actualFieldWriter.accept(AuthContext.PRINCIPAL_ID, actualContext.principalId());
            actualFieldWriter.accept(AuthContext.PRINCIPAL_NAME, actualContext.principalName());
            final String principalPermissionToken = actualContext.principalPermissionToken();
            if (principalPermissionToken != null) {
                actualFieldWriter.accept(AuthContext.PRINCIPAL_PERMISSION_TOKEN, principalPermissionToken);
            }
        }

        @SuppressWarnings("NullAway")
        @Override
        public Optional<AuthContext> decode(final Function<String, @Nullable String> fieldReader) {
            Objects.requireNonNull(fieldReader, "fieldReader");
            final String principalTypeStr = fieldReader.apply(AuthContext.PRINCIPAL_TYPE);
            final String principalId = fieldReader.apply(AuthContext.PRINCIPAL_ID);
            final String principalName = fieldReader.apply(AuthContext.PRINCIPAL_NAME);
            if (principalTypeStr == null || principalId == null || principalName == null) {
                return Optional.empty();
            }
            final PrincipalType principalType = PrincipalType.parse(principalTypeStr);
            return Optional.of(new AuthContext(
                    principalType,
                    principalId,
                    principalName,
                    fieldReader.apply(AuthContext.PRINCIPAL_PERMISSION_TOKEN)));
        }
    }
}
