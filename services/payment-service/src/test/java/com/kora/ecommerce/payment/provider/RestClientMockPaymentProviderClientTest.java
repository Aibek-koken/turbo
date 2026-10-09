package com.kora.ecommerce.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientMockPaymentProviderClientTest {

    private static final String BASE_URL = "http://mock-payment-provider";
    private static final String AUTHORIZE_PATH = "/api/mock-payments/authorize";

    private MockRestServiceServer server;
    private PaymentProviderClient client;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.verify();
        }
    }

    @Test
    void mapsApprovedResponseToSuccessfulProviderResult() {
        bindServerClient();
        server.expect(requestTo(BASE_URL + AUTHORIZE_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.providerRequestId").isString())
                .andExpect(jsonPath("$.paymentId").isString())
                .andExpect(jsonPath("$.orderId").isString())
                .andExpect(jsonPath("$.customerId").value("jwt-customer-123"))
                .andExpect(jsonPath("$.amount").value(42.99))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andRespond(withSuccess("""
                        {
                          "outcome": "APPROVED",
                          "providerReference": "mock-provider-ref-123"
                        }
                        """, MediaType.APPLICATION_JSON));

        PaymentProviderResult result = client.authorize(request());

        assertThat(result.outcome()).isEqualTo(PaymentProviderOutcome.SUCCEEDED);
        assertThat(result.providerReference()).isEqualTo("mock-provider-ref-123");
        assertThat(result.failureReason()).isNull();
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void mapsPaymentRequiredResponseToDeclinedProviderResult() {
        bindServerClient();
        server.expect(requestTo(BASE_URL + AUTHORIZE_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "outcome": "DECLINED",
                                  "providerReference": "mock-provider-decline-123"
                                }
                                """));

        PaymentProviderResult result = client.authorize(request());

        assertThat(result.outcome()).isEqualTo(PaymentProviderOutcome.DECLINED);
        assertThat(result.providerReference()).isEqualTo("mock-provider-decline-123");
        assertThat(result.failureReason()).isEqualTo("PAYMENT_DECLINED");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void mapsRequestTimeoutToRetryableProviderResult() {
        MockPaymentProviderProperties properties = properties();
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory((uri, httpMethod) -> {
                    throw new SocketTimeoutException("mock timeout");
                });
        PaymentProviderClient timeoutClient = new RestClientMockPaymentProviderClient(
                restClientBuilder,
                new ObjectMapper(),
                properties);

        PaymentProviderResult result = timeoutClient.authorize(request());

        assertThat(result.outcome()).isEqualTo(PaymentProviderOutcome.TIMED_OUT);
        assertThat(result.failureReason()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void mapsServerErrorToRetryableProviderResult() {
        bindServerClient();
        server.expect(requestTo(BASE_URL + AUTHORIZE_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        PaymentProviderResult result = client.authorize(request());

        assertThat(result.outcome()).isEqualTo(PaymentProviderOutcome.PROVIDER_5XX);
        assertThat(result.failureReason()).isEqualTo("PROVIDER_5XX_HTTP_500");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void mapsMalformedApprovedBodyToExplicitFailureResult() {
        bindServerClient();
        server.expect(requestTo(BASE_URL + AUTHORIZE_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "outcome": "APPROVED"
                        }
                        """, MediaType.APPLICATION_JSON));

        PaymentProviderResult result = client.authorize(request());

        assertThat(result.outcome()).isEqualTo(PaymentProviderOutcome.MALFORMED_RESPONSE);
        assertThat(result.failureReason()).isEqualTo("PROVIDER_MALFORMED_RESPONSE");
        assertThat(result.retryable()).isFalse();
    }

    private void bindServerClient() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new RestClientMockPaymentProviderClient(restClientBuilder, new ObjectMapper(), properties());
    }

    private static MockPaymentProviderProperties properties() {
        MockPaymentProviderProperties properties = new MockPaymentProviderProperties();
        properties.setBaseUrl(URI.create(BASE_URL));
        properties.setAuthorizePath(AUTHORIZE_PATH);
        return properties;
    }

    private static PaymentProviderRequest request() {
        return new PaymentProviderRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "jwt-customer-123",
                new BigDecimal("42.9900"),
                "USD");
    }
}
