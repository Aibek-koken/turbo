package com.kora.ecommerce.order.catalog;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.kora.ecommerce.order.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RestClientCatalogProductClient implements CatalogProductClient {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final RestClient restClient;

    RestClientCatalogProductClient(RestClient.Builder restClientBuilder, CatalogClientProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl().toString())
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public ProductSnapshot resolveProductSnapshot(UUID productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }

        CatalogProductDetailResponse response = fetchProduct(productId);
        return toSnapshot(productId, response);
    }

    private CatalogProductDetailResponse fetchProduct(UUID productId) {
        try {
            CatalogProductDetailResponse response = restClient.get()
                    .uri("/api/catalog/products/{productId}", productId)
                    .headers(headers -> addForwardedHeaders(headers, productId))
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                            (request, clientResponse) -> {
                                throw ProductSnapshotResolutionException.missing(productId);
                            })
                    .onStatus(HttpStatusCode::isError,
                            (request, clientResponse) -> {
                                throw ProductSnapshotResolutionException.unavailable(productId);
                            })
                    .body(CatalogProductDetailResponse.class);

            if (response == null) {
                throw ProductSnapshotResolutionException.malformed(productId);
            }
            return response;
        } catch (ProductSnapshotResolutionException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw ProductSnapshotResolutionException.unavailable(productId, exception);
        } catch (RestClientException exception) {
            throw ProductSnapshotResolutionException.malformed(productId, exception);
        }
    }

    private void addForwardedHeaders(HttpHeaders headers, UUID productId) {
        headers.set(HttpHeaders.AUTHORIZATION, resolveBearerToken(productId));
        resolveCorrelationId().ifPresent(correlationId ->
                headers.set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId));
    }

    private String resolveBearerToken(UUID productId) {
        return currentRequest()
                .map(request -> request.getHeader(HttpHeaders.AUTHORIZATION))
                .map(String::trim)
                .filter(RestClientCatalogProductClient::isBearerToken)
                .orElseThrow(() -> ProductSnapshotResolutionException.authenticationRequired(productId));
    }

    private Optional<String> resolveCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (StringUtils.hasText(correlationId)) {
            return Optional.of(correlationId.trim());
        }
        return currentRequest()
                .map(request -> request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .map(String::trim)
                .filter(StringUtils::hasText);
    }

    private ProductSnapshot toSnapshot(UUID requestedProductId, CatalogProductDetailResponse response) {
        if (!requestedProductId.equals(response.id())) {
            throw ProductSnapshotResolutionException.malformed(requestedProductId);
        }
        if (!StringUtils.hasText(response.status())) {
            throw ProductSnapshotResolutionException.malformed(requestedProductId);
        }
        if (!ACTIVE_STATUS.equals(response.status())) {
            throw ProductSnapshotResolutionException.inactive(requestedProductId);
        }
        if (!StringUtils.hasText(response.sku())
                || !StringUtils.hasText(response.name())
                || response.priceAmount() == null
                || response.priceAmount().compareTo(BigDecimal.ZERO) < 0
                || !isCurrencyCode(response.currency())) {
            throw ProductSnapshotResolutionException.malformed(requestedProductId);
        }

        return new ProductSnapshot(
                response.id(),
                response.sku().trim(),
                response.name().trim(),
                response.priceAmount(),
                response.currency().trim());
    }

    private static boolean isBearerToken(String authorization) {
        return authorization.length() > "Bearer ".length()
                && authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length());
    }

    private static boolean isCurrencyCode(String currency) {
        return StringUtils.hasText(currency) && currency.matches("[A-Z]{3}");
    }

    private static Optional<HttpServletRequest> currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return Optional.ofNullable(servletRequestAttributes.getRequest());
        }
        return Optional.empty();
    }

    private record CatalogProductDetailResponse(
            UUID id,
            String sku,
            String name,
            BigDecimal priceAmount,
            String currency,
            String status) {
    }
}
