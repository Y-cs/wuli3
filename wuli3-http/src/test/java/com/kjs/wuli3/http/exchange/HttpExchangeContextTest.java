package com.kjs.wuli3.http.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.http.logging.HttpLogOptions;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

/**
 * 验证请求诊断条目的复制、并发追加、终结快照与公共输入边界。
 *
 * @author 国杨 create on 2026/11/16 11:33
 */
class HttpExchangeContextTest {

    @Test
    void copiesEntriesAndFreezesTheSnapshotAfterConcurrentAppends() {
        final HttpExchangeContext context = new HttpExchangeContext("test", HttpLogOptions.defaults());
        final Map<String, Object> entry = new HashMap<>(Map.of("step", "original"));
        context.addLog(entry);
        entry.put("step", "changed");
        Flux.range(0, 32)
                .flatMap(index -> reactor.core.publisher.Mono.fromRunnable(() -> context.addLog(Map.of("index", index)))
                        .subscribeOn(Schedulers.parallel()))
                .then()
                .block(Duration.ofSeconds(5));
        final Map<String, Object> snapshot = context.finishLog(SignalType.ON_COMPLETE, null);
        assertThat(snapshot.get("diagnostics"))
                .asInstanceOf(LIST)
                .hasSize(33)
                .first()
                .isEqualTo(Map.of("step", "original"));
        context.addLog(Map.of("step", "late"));
        assertThat(snapshot.get("diagnostics")).asInstanceOf(LIST).hasSize(33);
        assertThatThrownBy(() -> snapshot.put("changed", true)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // 验证外部诊断数据进入统一错误码异常链。
    void rejectsInvalidDiagnosticEntries() {
        final HttpExchangeContext context = new HttpExchangeContext("test", HttpLogOptions.defaults());
        assertThatThrownBy(() -> context.addLog(null)).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> context.addLog(Map.of(" ", "value"))).isInstanceOf(ErrorCodeException.class);
        final Map<String, Object> entry = new HashMap<>();
        entry.put("key", null);
        assertThatThrownBy(() -> context.addLog(entry)).isInstanceOf(ErrorCodeException.class);
    }
}
