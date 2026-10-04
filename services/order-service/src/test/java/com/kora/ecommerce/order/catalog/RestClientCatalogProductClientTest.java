package com.kora.ecommerce.order.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

import com.kora.ecommerce.order.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RestClientCatalogProductClientTest {

    private static final String BASE_URL = "http://catalog-test";

    private MockRestServiceServer server;
    private CatalogProductClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();

        CatalogClientProperties properties = new CatalogClientProperties();
        properties.setBaseUrl(URI.create(BASE_URL));
        client = new RestClientCatalogProductClient(restClientBuilder, properties);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
        server.verify();
    }

    @Test
    void mapsActiveProductDetailToOrderOwnedSnapshotAndForwardsRequestHeaders() {
        UUID productId = UUID.randomUUID();
        bindRequest("customer-token", "corr-123");
        server.expect(requestTo(productUrl(productId)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer customer-token"))
                .andExpect(header(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-123"))
                .andRespond(withSuccess("""
                        {
                          "id": "%s",
                          "sku": "SKU-001",
                          "name": "Snapshot Jacket",
                          "description": "Ignored by Order Service",
                          "priceAmount": 12.5000,
                          "currency": "USD",
                          "status": "ACTIVE",
                          "category": {"id": "%s", "name": "Outerwear", "slug": "outerwear"},
                          "attributes": []
                        }
                        """.formatted(productId, UUID.randomUUID()), MediaType.APPLICATION_JSON));

        ProductSnapshot snapshot = client.resolveProductSnapshot(productId);

        assertThat(snapshot.productId()).isEqualTo(productId);
        assertThat(snapshot.sku()).isEqualTo("SKU-001");
        assertThat(snapshot.name()).isEqualTo("Snapshot Jacket");
        assertThat(snapshot.unitPriceAmount()).isEqualByComparingTo(new BigDecimal("12.5000"));
        assertThat(snapshot.currency()).isEqualTo("USD");
    }

    @Test
    void convertsMissingCatalogProductToExplicitFailure() {
        UUID productId = UUID.randomUUID();
        bindRequest("customer-token", "corr-123");
        server.expect(requestTo(productUrl(productId)))
                .andRespond(withResourceNotFound());

        assertSnapshotFailure(productId, ProductSnapshotFailure.MISSING);
    }

    @Test
    void convertsInactiveCatalogProductToExplicitFailure() {
        UUID productId = UUID.randomUUID();
        bindRequest("customer-token", "corr-123");
        server.expect(requestTo(productUrl(productId)))
                .andRespond(withSuccess("""
                        {
                          "id": "%s",
                          "sku": "SKU-002",
                          "name": "Draft Jacket",
                          "priceAmount": 15.0000,
                          "currency": "USD",
                          "status": "DRAFT",
                          "category": {"id": "%s", "name": "Outerwear", "slug": "outerwear"},
                          "attributes": []
                        }
                        """.formatted(productId, UUID.randomUUID()), MediaType.APPLICATION_JSON));

        assertSnapshotFailure(productId, ProductSnapshotFailure.INACTIVE);
    }

    @Test
    void convertsMalformedCatalogProductToExplicitFailure() {
        UUID productId = UUID.randomUUID();
        bindRequest("customer-token", "corr-123");
        server.expect(requestTo(productUrl(productId)))
                .andRespond(withSuccess("""
                        {
                          "id": "%s",
                          "sku": "",
                          "name": "Broken Jacket",
                          "priceAmount": 15.0000,
                          "currency": "USD",
                          "status": "ACTIVE",
                          "category": {"id": "%s", "name": "Outerwear", "slug": "outerwear"},
                          "attributes": []
                        }
                        """.formatted(productId, UUID.randomUUID()), MediaType.APPLICATION_JSON));

        assertSnapshotFailure(productId, ProductSnapshotFailure.MALFORMED);
    }

    @Test
    void convertsUnavailableCatalogProductLookupToExplicitFailure() {
        UUID productId = UUID.randomUUID();
        bindRequest("customer-token", "corr-123");
        server.expect(requestTo(productUrl(productId)))
                .andRespond(withServerError());

        assertSnapshotFailure(productId, ProductSnapshotFailure.UNAVAILABLE);
    }

    @Test
    void rejectsMissingBearerTokenBeforeCallingCatalog() {
        UUID productId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-123");
        bindRequest(request);
        server.expect(never(), requestTo(productUrl(productId)));

        assertSnapshotFailure(productId, ProductSnapshotFailure.AUTHENTICATION_REQUIRED);
    }

    private void assertSnapshotFailure(UUID productId, ProductSnapshotFailure failure) {
        assertThatThrownBy(() -> client.resolveProductSnapshot(productId))
                .isInstanceOfSatisfying(ProductSnapshotResolutionException.class, exception -> {
                    assertThat(exception.productId()).isEqualTo(productId);
                    assertThat(exception.failure()).isEqualTo(failure);
                    assertThat(exception.apiStatus()).isEqualTo(failure.apiStatus());
                });
    }

    private static void bindRequest(String bearerToken, String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);
        bindRequest(request);
    }

    private static void bindRequest(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static String productUrl(UUID productId) {
        return BASE_URL + "/api/catalog/products/" + productId;
    }
}
