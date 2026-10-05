package com.kora.ecommerce.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void preservesIncomingCorrelationIdOnForwardedRequestAndResponse() {
        AtomicReference<String> forwardedCorrelationId = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/orders")
                .header(CorrelationIdWebFilter.CORRELATION_ID_HEADER, "  purchase-flow-123  "));

        filter.filter(exchange, chainExchange -> {
            forwardedCorrelationId.set(chainExchange.getRequest()
                    .getHeaders()
                    .getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER));
            return chainExchange.getResponse().setComplete();
        }).block();

        assertThat(forwardedCorrelationId).hasValue("purchase-flow-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER))
                .isEqualTo("purchase-flow-123");
    }

    @Test
    void generatesCorrelationIdWhenRequestDoesNotProvideOne() {
        AtomicReference<String> forwardedCorrelationId = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));

        filter.filter(exchange, chainExchange -> {
            forwardedCorrelationId.set(chainExchange.getRequest()
                    .getHeaders()
                    .getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER));
            return chainExchange.getResponse().setComplete();
        }).block();

        assertThat(forwardedCorrelationId.get()).isNotBlank();
        assertThat(UUID.fromString(forwardedCorrelationId.get())).isNotNull();
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER))
                .isEqualTo(forwardedCorrelationId.get());
    }
}
