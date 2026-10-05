package com.kora.ecommerce.order.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @BeforeEach
    void clearMdcBeforeTest() {
        MDC.clear();
    }

    @AfterEach
    void clearMdcAfterTest() {
        MDC.clear();
    }

    @Test
    void preservesIncomingCorrelationIdInResponseAndMdcDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "  purchase-flow-123  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcCorrelationId = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                mdcCorrelationId.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(mdcCorrelationId).hasValue("purchase-flow-123");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("purchase-flow-123");
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }

    @Test
    void generatesCorrelationIdWhenRequestDoesNotProvideOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcCorrelationId = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                mdcCorrelationId.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(mdcCorrelationId.get()).isNotBlank();
        assertThat(UUID.fromString(mdcCorrelationId.get())).isNotNull();
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo(mdcCorrelationId.get());
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }
}
