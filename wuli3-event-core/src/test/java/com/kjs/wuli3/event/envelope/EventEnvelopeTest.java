package com.kjs.wuli3.event.envelope;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {
    @Test
    void rejectsBlankProtocolIdentifiers() {
        assertThatThrownBy(() -> new EventEnvelope<>(" ", "order.paid.v1", "event-1", Instant.EPOCH, "payload"))
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("topic cannot be blank");
        assertThatThrownBy(() -> new EventEnvelope<>("orders", " ", "event-1", Instant.EPOCH, "payload"))
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("eventType cannot be blank");
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullPayload() {
        assertThatThrownBy(() -> new EventEnvelope<>("orders", "order.paid.v1", "event-1", Instant.EPOCH, null))
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("payload cannot be null");
    }

    @Test
    void templateValidatesTopicBeforePublishing() {
        assertThatThrownBy(() -> EventEnvelopeTemplate.of(" ", "order.paid.v1"))
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("topic cannot be blank");
    }
}
