package com.kora.ecommerce.payment.provider;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class RestClientMockPaymentProviderClient implements PaymentProviderClient {

    private static final int MAX_PROVIDER_REFERENCE_LENGTH = 128;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String authorizePath;

    RestClientMockPaymentProviderClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            MockPaymentProviderProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl().toString())
                .build();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.authorizePath = properties.getAuthorizePath();
    }

    @Override
    public PaymentProviderResult authorize(PaymentProviderRequest request) {
        Objects.requireNonNull(request, "request is required");
        try {
            String requestBody = objectMapper.writeValueAsString(ProviderAuthorizeRequest.from(request));
            return restClient.post()
                    .uri(authorizePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange((clientRequest, response) -> mapResponse(response));
        } catch (JsonProcessingException exception) {
            return PaymentProviderResult.malformedResponse();
        } catch (MalformedProviderResponseException exception) {
            return PaymentProviderResult.malformedResponse();
        } catch (ResourceAccessException exception) {
            return PaymentProviderResult.timedOut();
        } catch (RestClientException exception) {
            return PaymentProviderResult.malformedResponse();
        }
    }

    private PaymentProviderResult mapResponse(ClientHttpResponse response) throws IOException {
        int httpStatus = response.getStatusCode().value();
        if (response.getStatusCode().is5xxServerError()) {
            return PaymentProviderResult.provider5xx(httpStatus);
        }
        if (httpStatus == HttpStatus.PAYMENT_REQUIRED.value()) {
            Optional<ProviderAuthorizeResponse> body = readOptionalBody(response);
            String providerReference = body
                    .map(ProviderAuthorizeResponse::providerReference)
                    .map(RestClientMockPaymentProviderClient::providerReferenceOrMalformed)
                    .orElse(null);
            return PaymentProviderResult.declined(providerReference);
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            return PaymentProviderResult.malformedResponse();
        }

        ProviderAuthorizeResponse body = readOptionalBody(response)
                .orElseThrow(MalformedProviderResponseException::new);
        String outcome = normalizeOutcome(body.outcome())
                .orElseThrow(MalformedProviderResponseException::new);
        if ("APPROVED".equals(outcome) || "SUCCEEDED".equals(outcome)) {
            String providerReference = providerReferenceOrMalformed(body.providerReference());
            if (providerReference == null) {
                throw new MalformedProviderResponseException();
            }
            return PaymentProviderResult.succeeded(providerReference);
        }
        if ("DECLINED".equals(outcome)) {
            return PaymentProviderResult.declined(providerReferenceOrMalformed(body.providerReference()));
        }
        throw new MalformedProviderResponseException();
    }

    private Optional<ProviderAuthorizeResponse> readOptionalBody(ClientHttpResponse response) throws IOException {
        byte[] responseBody = response.getBody().readAllBytes();
        if (responseBody.length == 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(responseBody, ProviderAuthorizeResponse.class));
        } catch (JsonProcessingException exception) {
            throw new MalformedProviderResponseException(exception);
        }
    }

    private static Optional<String> normalizeOutcome(String outcome) {
        if (!StringUtils.hasText(outcome)) {
            return Optional.empty();
        }
        return Optional.of(outcome.trim().toUpperCase(Locale.ROOT));
    }

    private static String providerReferenceOrMalformed(String providerReference) {
        if (!StringUtils.hasText(providerReference)) {
            return null;
        }
        String normalized = providerReference.trim();
        if (normalized.length() > MAX_PROVIDER_REFERENCE_LENGTH) {
            throw new MalformedProviderResponseException();
        }
        return normalized;
    }

    private record ProviderAuthorizeRequest(
            UUID providerRequestId,
            UUID paymentId,
            UUID orderId,
            String customerId,
            BigDecimal amount,
            String currency) {

        static ProviderAuthorizeRequest from(PaymentProviderRequest request) {
            return new ProviderAuthorizeRequest(
                    request.providerRequestId(),
                    request.paymentId(),
                    request.orderId(),
                    request.customerId(),
                    request.amount(),
                    request.currency());
        }
    }

    private record ProviderAuthorizeResponse(
            String outcome,
            String providerReference,
            String declineReason) {
    }

    private static final class MalformedProviderResponseException extends RuntimeException {

        private MalformedProviderResponseException() {
        }

        private MalformedProviderResponseException(Throwable cause) {
            super(cause);
        }
    }
}
