package com.kora.ecommerce.catalog.batch;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.kora.ecommerce.catalog.domain.ProductStatus;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
class SupplierProductCsvProcessor implements ItemProcessor<SupplierProductCsvRow, SupplierProductImportCommand> {

    private static final int SKU_MAX_LENGTH = 64;
    private static final int PRODUCT_NAME_MAX_LENGTH = 200;
    private static final int PRODUCT_DESCRIPTION_MAX_LENGTH = 2000;
    private static final int CATEGORY_SLUG_MAX_LENGTH = 180;
    private static final int CATEGORY_NAME_MAX_LENGTH = 160;
    private static final int CATEGORY_DESCRIPTION_MAX_LENGTH = 1000;
    private static final int CURRENCY_LENGTH = 3;
    private static final int ATTRIBUTE_KEY_MAX_LENGTH = 120;
    private static final int ATTRIBUTE_VALUE_MAX_LENGTH = 1000;
    private static final String ALLOWED_STATUSES = Arrays.stream(ProductStatus.values())
            .map(ProductStatus::name)
            .sorted()
            .collect(Collectors.joining(", "));

    @Override
    public SupplierProductImportCommand process(SupplierProductCsvRow item) {
        List<String> errors = new ArrayList<>();

        String sku = requiredText(item.sku(), "sku", errors);
        String productName = requiredText(item.productName(), "product_name", errors);
        String productDescription = optionalText(item.productDescription());
        String categorySlug = requiredText(item.categorySlug(), "category_slug", errors);
        String categoryName = requiredText(item.categoryName(), "category_name", errors);
        String categoryDescription = optionalText(item.categoryDescription());
        String currency = requiredText(item.currency(), "currency", errors);
        String status = requiredText(item.status(), "status", errors);

        validateMaxLength(sku, "sku", SKU_MAX_LENGTH, errors);
        validateMaxLength(productName, "product_name", PRODUCT_NAME_MAX_LENGTH, errors);
        validateMaxLength(productDescription, "product_description", PRODUCT_DESCRIPTION_MAX_LENGTH, errors);
        validateMaxLength(categorySlug, "category_slug", CATEGORY_SLUG_MAX_LENGTH, errors);
        validateMaxLength(categoryName, "category_name", CATEGORY_NAME_MAX_LENGTH, errors);
        validateMaxLength(categoryDescription, "category_description", CATEGORY_DESCRIPTION_MAX_LENGTH, errors);

        BigDecimal priceAmount = parsePrice(item.priceAmount(), errors);
        String normalizedCurrency = normalizeCurrency(currency, errors);
        ProductStatus productStatus = parseStatus(status, errors);
        Map<String, String> attributes = parseAttributes(item.attributes(), errors);

        if (!errors.isEmpty()) {
            throw new SupplierImportRowValidationException(item.rowNumber(), String.join("; ", errors));
        }

        return new SupplierProductImportCommand(
                sku,
                productName,
                productDescription,
                categorySlug,
                categoryName,
                categoryDescription,
                priceAmount,
                normalizedCurrency,
                productStatus,
                attributes);
    }

    private static BigDecimal parsePrice(String priceAmount, List<String> errors) {
        String required = requiredText(priceAmount, "price_amount", errors);
        if (required == null) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal parsed = new BigDecimal(required);
            if (parsed.signum() < 0) {
                errors.add("price_amount must be non-negative");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            errors.add("price_amount must be a valid decimal");
            return BigDecimal.ZERO;
        }
    }

    private static String normalizeCurrency(String currency, List<String> errors) {
        if (currency == null) {
            return null;
        }
        String normalized = currency.toUpperCase(Locale.ROOT);
        if (normalized.length() != CURRENCY_LENGTH || !normalized.chars().allMatch(Character::isLetter)) {
            errors.add("currency must be a 3-letter currency code");
        }
        return normalized;
    }

    private static ProductStatus parseStatus(String status, List<String> errors) {
        if (status == null) {
            return ProductStatus.DRAFT;
        }
        try {
            return ProductStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add("status must be one of " + ALLOWED_STATUSES);
            return ProductStatus.DRAFT;
        }
    }

    private static Map<String, String> parseAttributes(String attributes, List<String> errors) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (attributes == null || attributes.isBlank()) {
            return parsed;
        }

        for (String pair : attributes.split(";")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                errors.add("attributes must use key=value entries");
                continue;
            }
            String key = requiredText(parts[0], "attribute key", errors);
            String value = requiredText(parts[1], "attribute value", errors);
            validateMaxLength(key, "attribute key", ATTRIBUTE_KEY_MAX_LENGTH, errors);
            validateMaxLength(value, "attribute value", ATTRIBUTE_VALUE_MAX_LENGTH, errors);
            if (key != null && value != null) {
                parsed.put(key, value);
            }
        }
        return parsed;
    }

    private static String requiredText(String value, String fieldName, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(fieldName + " is required");
            return null;
        }
        return value.trim();
    }

    private static void validateMaxLength(String value, String fieldName, int maxLength, List<String> errors) {
        if (value != null && value.length() > maxLength) {
            errors.add(fieldName + " must be at most " + maxLength + " characters");
        }
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
