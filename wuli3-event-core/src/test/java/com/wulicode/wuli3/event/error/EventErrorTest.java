package com.wulicode.wuli3.event.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.wulicode.wuli3.core.error.model.ErrorOrigin;
import com.wulicode.wuli3.core.error.model.ErrorSeverity;
import org.junit.jupiter.api.Test;

class EventErrorTest {

    @Test
    void sendFailureUsesServerMetadata() {
        final SendFailedException exception = new SendFailedException("发送失败", new IllegalStateException());

        assertThat(exception.getOrigin()).isEqualTo(ErrorOrigin.SERVER);
        assertThat(exception.getSeverity()).isEqualTo(ErrorSeverity.CRITICAL);
    }
}
