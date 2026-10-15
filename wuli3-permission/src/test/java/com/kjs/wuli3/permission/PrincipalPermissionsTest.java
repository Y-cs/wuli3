package com.kjs.wuli3.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.permission.model.ActionCode;
import com.kjs.wuli3.permission.model.PermissionKey;
import com.kjs.wuli3.permission.model.PermissionToken;
import com.kjs.wuli3.permission.model.PrincipalPermissions;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 主体快照与纯位图判断测试。
 * @author 国杨 create on 2026/10/13 10:00
 */
class PrincipalPermissionsTest {
    @Test
    void checksAll64BitsIncludingSignBit() {
        final PrincipalPermissions principal =
                new PrincipalPermissions(new PermissionToken("token"), Map.of("ORDER", -1L));
        for (final ActionCode action : ActionCode.values()) {
            final PermissionKey key = new PermissionKey("ORDER", action);
            assertThat(action.getMask()).isEqualTo(1L << action.getBitIndex());
            assertThat(ActionCode.fromBitIndex(action.getBitIndex())).isEqualTo(action);
            assertThat(principal.contains(key)).isTrue();
        }
    }

    @Test
    void deniesMissingBitsAndResources() {
        final PrincipalPermissions principal =
                new PrincipalPermissions(new PermissionToken("token"), Map.of("ORDER", 8L));
        final PermissionKey read = new PermissionKey("ORDER", ActionCode.READ);
        final PermissionKey create = new PermissionKey("ORDER", ActionCode.CREATE);
        assertThat(principal.contains(create)).isFalse();
        assertThat(principal.contains(new PermissionKey("OTHER", ActionCode.READ)))
                .isFalse();
        assertThat(principal.contains(read)).isTrue();
    }

    @Test
    void snapshotsAreImmutableAndEmptyPermissionsAreValid() {
        final Map<String, Long> grants = new HashMap<>();
        grants.put("ORDER", 8L);
        final PermissionToken token = new PermissionToken("token");
        final PrincipalPermissions principal = new PrincipalPermissions(token, grants);
        grants.clear();
        assertThat(principal.token()).isEqualTo(token);
        assertThat(principal.permissions()).containsEntry("ORDER", 8L);
        assertThatThrownBy(() -> principal.permissions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new PrincipalPermissions(token, Map.of()).permissions()).isEmpty();
        assertThatThrownBy(() -> new PrincipalPermissions(token, Map.of(" ", 1L)))
                .isInstanceOf(ErrorCodeException.class);
    }
}
