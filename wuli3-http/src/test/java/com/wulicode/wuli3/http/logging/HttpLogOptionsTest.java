package com.wulicode.wuli3.http.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wulicode.wuli3.core.error.ErrorCodeException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 验证业务脱敏名称只补充内置名单，以及公共配置的校验与不可变边界。
 *
 * @author 国杨 create on 2026/11/13 17:39
 */
class HttpLogOptionsTest {

    @Test
    void defaultsKeepDetailsOffAndCopySelectedItems() {
        assertThat(HttpLogOptions.defaults().items()).isEmpty();
        assertThat(HttpLogOptions.defaults().maxBodyBytes()).isEqualTo(8192);
        final Set<HttpLogItem> items = new HashSet<>(Set.of(HttpLogItem.REQUEST_BODY));
        final HttpLogOptions options = new HttpLogOptions(true, true, Set.of(), items, 128);
        items.add(HttpLogItem.RESPONSE_BODY);
        assertThat(options.items()).containsExactly(HttpLogItem.REQUEST_BODY);
        assertThatThrownBy(() -> options.items().add(HttpLogItem.RESPONSE_BODY))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new HttpLogOptions(true, true, Set.of(), Set.of(), 0))
                .isInstanceOf(ErrorCodeException.class);
    }

    @Test
    void nullOrEmptySensitiveNamesKeepTheBuiltInNames() {
        final Set<String> defaults = HttpLogOptions.defaults().sensitiveNames();
        assertThat(new HttpLogOptions(true, true, null).sensitiveNames()).isEqualTo(defaults);
        assertThat(new HttpLogOptions(true, true, Set.of()).sensitiveNames()).isEqualTo(defaults);
    }

    @Test
    void customSensitiveNamesExtendTheBuiltInNamesAndAreCopied() {
        final Set<String> names = new HashSet<>(Set.of("account-number"));
        final HttpLogOptions options = new HttpLogOptions(true, true, names);
        names.add("later");
        assertThat(options.sensitiveNames())
                .containsAll(HttpLogOptions.defaults().sensitiveNames())
                .contains("account-number")
                .doesNotContain("later");
        assertThatThrownBy(() -> options.sensitiveNames().add("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // 验证外部集合包含非法 null 元素时进入统一错误链。
    void invalidSensitiveNamesUseTheUnifiedErrorBoundary() {
        final Set<String> names = new HashSet<>();
        names.add(null);
        assertThatThrownBy(() -> new HttpLogOptions(true, true, names)).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new HttpLogOptions(true, true, Set.of(" "))).isInstanceOf(ErrorCodeException.class);
    }
}
