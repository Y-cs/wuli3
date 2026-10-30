package com.kjs.wuli3.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.permission.annotation.PermissionResource;
import com.kjs.wuli3.permission.model.ActionCode;
import com.kjs.wuli3.permission.model.PermissionCode;
import com.kjs.wuli3.permission.model.PermissionToken;
import com.kjs.wuli3.permission.model.PrincipalPermissions;
import com.kjs.wuli3.permission.model.definition.PermissionActionDefinition;
import com.kjs.wuli3.permission.model.definition.PermissionResourceDefinition;
import com.kjs.wuli3.permission.store.LocalPermissionStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 资源枚举注册、引用和定义边界测试。
 * @author 国杨 create on 2026/10/13 10:00
 */
class LocalPermissionStoreTest {
    @Test
    void serviceScopeQualifiesExportedResourcesAndAllReferenceStyles() {
        final LocalPermissionStore catalog = new LocalPermissionStore("order-service", List.of(ReadOnly.class));
        final LocalPermissionStore other = new LocalPermissionStore("other-service", List.of(ReadOnly.class));
        final var key = PermissionResolver.resolve(catalog, "ORDER:READ", PermissionCode.class);
        assertThat(key.resourceCode()).isEqualTo("order-service:ORDER");
        assertThat(key)
                .isSameAs(PermissionResolver.resolve(catalog, ReadOnly.READ))
                .isSameAs(PermissionResolver.resolve(catalog, "READ", ReadOnly.class));
        assertThat(catalog.resources().getFirst().resourceCode()).isEqualTo(key.resourceCode());
        assertThat(catalog.requirement()).containsExactlyEntriesOf(java.util.Map.of("order-service:ORDER", 8L));
        assertThat(catalog.key("ORDER:READ")).isSameAs(key);
        assertThat(catalog.resource("ORDER")).isSameAs(catalog.resource("order-service:ORDER"));
        assertThat(key).isNotEqualTo(PermissionResolver.resolve(other, ReadOnly.READ));
        assertThat(catalog.resource("other-service:ORDER")).isNull();
        final PermissionToken token = new PermissionToken("token");
        assertThat(new PrincipalPermissions(token, java.util.Map.of("order-service:ORDER", 8L)).contains(key))
                .isTrue();
        assertThat(new PrincipalPermissions(token, java.util.Map.of("other-service:ORDER", 8L)).contains(key))
                .isFalse();
        assertThat(new PrincipalPermissions(token, java.util.Map.of("ORDER", 8L)).contains(key))
                .isFalse();
    }

    @Test
    void invalidServiceScopesFailAtThePublicBoundary() {
        for (final String serviceCode : List.of(" ", "bad:name", "bad/name", "a".repeat(65))) {
            assertThatThrownBy(() -> new LocalPermissionStore(serviceCode, List.of(ReadOnly.class)))
                    .isInstanceOf(ErrorCodeException.class);
        }
    }

    @Test
    void resolvesBothReferencesAndEnumWithConstantSpecificBody() {
        final LocalPermissionStore catalog = new LocalPermissionStore(List.of(ReadOnly.class, ReadOnly.class));
        assertThat(catalog.resources()).hasSize(1);
        assertThat(PermissionResolver.resolve(catalog, "ORDER:READ", PermissionCode.class))
                .isSameAs(PermissionResolver.resolve(catalog, "READ", ReadOnly.class))
                .isSameAs(PermissionResolver.resolve(catalog, ReadOnly.READ));
        assertThat(catalog.resources().getFirst().description()).isEqualTo("订单");
        assertThat(catalog.resources().getFirst().enumClass()).isEqualTo(ReadOnly.class);
        assertThat(catalog.resources().getFirst().resourceCode()).isEqualTo("ORDER");
        assertThat(catalog.requirement()).containsEntry("ORDER", 8L);
        assertThatThrownBy(() -> catalog.resources().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void completeEnumDeterminesScopeAndReferenceErrorsFail() {
        final LocalPermissionStore catalog = new LocalPermissionStore(List.of(OrderPermissions.class));
        assertThat(catalog.resources().getFirst().permissions()).hasSize(64);
        assertThat(catalog.requirement()).containsEntry("ORDER", -1L);
        assertThatThrownBy(() -> PermissionResolver.resolve(catalog, "READ", PermissionCode.class))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> PermissionResolver.resolve(catalog, "ORDER:READ", OrderPermissions.class))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> PermissionResolver.resolve(catalog, "ORDER:MISSING", PermissionCode.class))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> PermissionResolver.resolve(catalog, "OTHER:READ", PermissionCode.class))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> PermissionResolver.resolve(catalog, "READ", ReadOnly.class))
                .isInstanceOf(ErrorCodeException.class);
    }

    @Test
    void invalidDefinitionsFailAndDisplayChangesDoNotChangeFingerprint() {
        assertThatThrownBy(() -> new LocalPermissionStore(List.of(ReadOnly.class, OrderPermissions.class)))
                .isInstanceOf(ErrorCodeException.class);
        for (final Class<?> invalid :
                List.of(String.class, MissingResource.class, DuplicateAction.class, BlankDescription.class)) {
            assertThatThrownBy(() -> new LocalPermissionStore(List.of(invalid))).isInstanceOf(ErrorCodeException.class);
        }
        assertThat(new LocalPermissionStore(List.of(ReadOnly.class)).requirement())
                .isEqualTo(new LocalPermissionStore(List.of(Renamed.class)).requirement())
                .isNotEqualTo(new LocalPermissionStore(List.of(OrderPermissions.class)).requirement());
    }

    @Test
    void resourceMetadataCopiesPermissionsAndRejectsInvalidInputs() {
        final List<PermissionActionDefinition> permissions =
                new ArrayList<>(List.of(new PermissionActionDefinition("READ", ActionCode.READ, "查询")));
        final PermissionResourceDefinition resource =
                new PermissionResourceDefinition("ORDER", "订单", ReadOnly.class, permissions);
        permissions.clear();
        assertThat(resource.permissions()).hasSize(1);
        assertThatThrownBy(() -> resource.permissions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new PermissionResourceDefinition(" ", "订单", ReadOnly.class, List.of()))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() ->
                        new PermissionResourceDefinition("order-service:ORDER:READ", "订单", ReadOnly.class, List.of()))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new PermissionResourceDefinition("order", "订单", ReadOnly.class, List.of()))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new PermissionResourceDefinition("ORDER-1", "订单", ReadOnly.class, List.of()))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new PermissionResourceDefinition("ORDER", " ", ReadOnly.class, List.of()))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new PermissionResourceDefinition("ORDER", "订单", PermissionCode.class, List.of()))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new PermissionResourceDefinition(
                        "ORDER", "订单", ReadOnly.class, Arrays.asList((PermissionActionDefinition) null)))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new PermissionActionDefinition(" ", ActionCode.READ, "查询"))
                .isInstanceOf(ErrorCodeException.class);
    }

    @PermissionResource(value = "ORDER", description = "订单")
    enum ReadOnly implements PermissionCode {
        READ {
            @Override
            public ActionCode getAction() {
                return ActionCode.READ;
            }
        };

        @Override
        public String getDescription() {
            return "查询订单";
        }
    }

    @PermissionResource(value = "ORDER", description = "描述变更")
    enum Renamed implements PermissionCode {
        QUERY;

        @Override
        public ActionCode getAction() {
            return ActionCode.READ;
        }

        @Override
        public String getDescription() {
            return "读取";
        }
    }

    enum MissingResource implements PermissionCode {
        READ;

        @Override
        public ActionCode getAction() {
            return ActionCode.READ;
        }

        @Override
        public String getDescription() {
            return "查询";
        }
    }

    @PermissionResource(value = "ORDER", description = "订单")
    enum DuplicateAction implements PermissionCode {
        FIRST,
        SECOND;

        @Override
        public ActionCode getAction() {
            return ActionCode.READ;
        }

        @Override
        public String getDescription() {
            return "重复";
        }
    }

    @PermissionResource(value = "ORDER", description = "订单")
    enum BlankDescription implements PermissionCode {
        READ;

        @Override
        public ActionCode getAction() {
            return ActionCode.READ;
        }

        @Override
        public String getDescription() {
            return " ";
        }
    }
}
