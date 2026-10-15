package com.kjs.wuli3.permission;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.permission.error.PermissionErrors;
import com.kjs.wuli3.permission.model.PermissionCode;
import com.kjs.wuli3.permission.model.PermissionKey;
import com.kjs.wuli3.permission.model.PermissionToken;
import com.kjs.wuli3.permission.model.PrincipalPermissions;
import com.kjs.wuli3.permission.spi.PrincipalPermissionLoader;
import com.kjs.wuli3.permission.store.LocalPermissionStore;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.store.ContextReader;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 读取当前可信上下文并加载主体权限的统一鉴权入口；每次判断独立加载，缓存策略由加载器提供。
 *
 * @author 国杨 create on 2026/10/14 10:00
 */
public final class PermissionChecker {
    private final LocalPermissionStore store;
    private final PrincipalPermissionLoader loader;
    private final ContextReader reader;

    /** 装配本地声明、外部权限加载器及当前上下文读取器。 */
    public PermissionChecker(
            final LocalPermissionStore store, final PrincipalPermissionLoader loader, final ContextReader reader) {
        this.store = Objects.requireNonNull(store, "本地权限存储不能为空");
        this.loader = Objects.requireNonNull(loader, "权限加载器不能为空");
        this.reader = Objects.requireNonNull(reader, "上下文读取器不能为空");
    }

    /** 判断当前主体是否拥有指定权限；未提供可信 Token 时返回 false。 */
    public boolean has(final PermissionKey key) {
        // has 是查询操作：没有可信 Token 时返回 false，不触发权限加载器。
        Asserts.whenNull(key).throwIllegalArgumentException("权限标识不能为空");
        return this.currentToken()
                .map(this::load)
                .map(permissions -> permissions.contains(key))
                .orElse(false);
    }

    /** 解析本地声明枚举并判断当前主体是否拥有对应权限。 */
    public boolean has(final PermissionCode permission) {
        return this.has(PermissionResolver.resolve(this.store, permission));
    }

    /** 要求当前主体拥有指定权限；缺少 Token 或权限时拒绝执行。 */
    public void require(final PermissionKey key) {
        Asserts.whenNull(key).throwIllegalArgumentException("权限标识不能为空");
        this.requireAll(List.of(key));
    }

    /** 解析本地声明枚举并要求当前主体拥有对应权限。 */
    public void require(final PermissionCode permission) {
        this.require(PermissionResolver.resolve(this.store, permission));
    }

    /** 使用一次加载的快照判断全部权限；集合不得为空或包含 null。 */
    public void requireAll(final Collection<PermissionKey> keys) {
        this.check(keys, true);
    }

    /** 使用一次加载的快照判断至少一项权限；集合不得为空或包含 null。 */
    public void requireAny(final Collection<PermissionKey> keys) {
        this.check(keys, false);
    }

    private void check(final Collection<PermissionKey> keys, final boolean all) {
        // 空集合属于调用错误，不能利用 allMatch 对空集合返回 true 的规则跳过鉴权。
        Asserts.whenEmptyCollection(keys).throwIllegalArgumentException("待校验权限不能为空");
        Asserts.whenTrue(keys.stream().anyMatch(Objects::isNull)).throwIllegalArgumentException("待校验权限不能包含 null");
        final PermissionToken token =
                this.currentToken().orElseThrow(() -> new ErrorCodeException(PermissionErrors.UNAUTHORIZED));
        // 一个组合判断只加载一次快照，保证 all/any 使用同一权限版本。
        final PrincipalPermissions permissions = this.load(token);
        final boolean allowed = all
                ? keys.stream().allMatch(permissions::contains)
                : keys.stream().anyMatch(permissions::contains);
        if (!allowed) {
            throw new ErrorCodeException(PermissionErrors.FORBIDDEN);
        }
    }

    private Optional<PermissionToken> currentToken() {
        // Token 只信任传播上下文，不从用户 ID 或业务参数推导。
        return this.reader
                .get(ContextKey.of(AuthContext.class))
                .map(AuthContext::principalPermissionToken)
                .map(PermissionToken::new);
    }

    private PrincipalPermissions load(final PermissionToken token) {
        // 加载器负责 Redis、用户服务和缓存；Checker 只校验返回快照属于当前 Token。
        final PrincipalPermissions permissions = this.loader.load(token, this.store.requirement());
        if (permissions == null || !token.equals(permissions.token())) {
            throw new IllegalStateException("权限加载器必须返回当前 Token 的权限快照");
        }
        return permissions;
    }
}
