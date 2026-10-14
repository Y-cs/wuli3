package com.kjs.wuli3.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.permission.annotation.PermissionResource;
import com.kjs.wuli3.permission.error.PermissionErrors;
import com.kjs.wuli3.permission.model.ActionCode;
import com.kjs.wuli3.permission.model.PermissionCode;
import com.kjs.wuli3.permission.model.PermissionKey;
import com.kjs.wuli3.permission.model.PrincipalPermissions;
import com.kjs.wuli3.permission.store.LocalPermissionStore;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 验证代码鉴权随当前 Token 加载快照及组合判断的一致性。
 *
 * @author 国杨 create on 2026/10/14 10:00
 */
class PermissionCheckerTest {
    private final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();
    private final LocalPermissionStore store = new LocalPermissionStore(List.of(OrderPermission.class));
    private final PermissionKey read = new PermissionKey("ORDER", ActionCode.READ);
    private final PermissionKey write = new PermissionKey("ORDER", ActionCode.UPDATE);
    private final AtomicInteger loads = new AtomicInteger();
    private final PermissionChecker checker = new PermissionChecker(
            this.store,
            (token, resources) -> {
                this.loads.incrementAndGet();
                assertThat(resources).containsExactlyEntriesOf(Map.of("ORDER", 8L));
                return new PrincipalPermissions(
                        token, Map.of("ORDER", token.token().equals("allow") ? 8L : 0L));
            },
            this.backend);

    @Test
    void readsCurrentTokenForEveryCallAndSupportsDeclaredEnums() {
        this.auth("allow", () -> {
            assertThat(this.checker.has(this.read)).isTrue();
            assertThat(this.checker.has(OrderPermission.READ)).isTrue();
            this.checker.require(OrderPermission.READ);
            this.checker.require(this.read);
        });
        this.auth("deny", () -> {
            assertThat(this.checker.has(this.read)).isFalse();
            assertThatThrownBy(() -> this.checker.require(this.read))
                    .isInstanceOfSatisfying(
                            ErrorCodeException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(PermissionErrors.FORBIDDEN));
        });
        assertThat(this.loads).hasValue(6);
    }

    @Test
    void combinesPermissionsUsingOneSnapshotPerCall() {
        this.auth("allow", () -> {
            this.checker.requireAll(List.of(this.read, this.read));
            assertThat(this.loads).hasValue(1);
            this.checker.requireAny(List.of(this.write, this.read));
            assertThat(this.loads).hasValue(2);
            assertThatThrownBy(() -> this.checker.requireAll(List.of(this.read, this.write)))
                    .isInstanceOf(ErrorCodeException.class);
            assertThat(this.loads).hasValue(3);
            assertThatThrownBy(() -> this.checker.requireAny(List.of(this.write)))
                    .isInstanceOf(ErrorCodeException.class);
            assertThat(this.loads).hasValue(4);
        });
    }

    @Test
    void missingTokenDoesNotLoadAndRequiresAuthentication() {
        this.assertUnauthorized();
        this.backend.run(ContextState.of(new AuthContext(PrincipalType.ADMIN, "1", "用户")), this::assertUnauthorized);
        assertThat(this.loads).hasValue(0);
    }

    @Test
    @SuppressWarnings("NullAway") // 验证公共 API 非法参数不会触发权限加载。
    void rejectsEmptyAndNullInputsBeforeLoading() {
        assertThatThrownBy(() -> this.checker.has((PermissionKey) null)).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> this.checker.has((PermissionCode) null)).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> this.checker.require((PermissionKey) null)).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> this.checker.require((PermissionCode) null)).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> this.checker.requireAll(null)).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> this.checker.requireAny(null)).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> this.checker.requireAll(List.of())).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> this.checker.requireAny(List.of())).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> this.checker.requireAll(Arrays.asList(this.read, null)))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> this.checker.requireAny(Arrays.asList(this.read, null)))
                .isInstanceOf(ErrorCodeException.class);
        assertThat(this.loads).hasValue(0);
    }

    private void assertUnauthorized() {
        assertThat(this.checker.has(this.read)).isFalse();
        for (final Runnable check : List.<Runnable>of(
                () -> this.checker.require(this.read),
                () -> this.checker.requireAll(List.of(this.read)),
                () -> this.checker.requireAny(List.of(this.read)))) {
            assertThatThrownBy(check::run)
                    .isInstanceOfSatisfying(
                            ErrorCodeException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(PermissionErrors.UNAUTHORIZED));
        }
    }

    private void auth(final String token, final Runnable action) {
        this.backend.run(ContextState.of(new AuthContext(PrincipalType.ADMIN, "1", "用户", token)), action);
    }

    @PermissionResource(value = "ORDER", description = "订单")
    enum OrderPermission implements PermissionCode {
        READ;

        @Override
        public ActionCode getAction() {
            return ActionCode.READ;
        }

        @Override
        public String getDescription() {
            return "查询订单";
        }
    }
}
