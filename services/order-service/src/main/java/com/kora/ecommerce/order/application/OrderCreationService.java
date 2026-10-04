package com.kora.ecommerce.order.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kora.ecommerce.order.catalog.CatalogProductClient;
import com.kora.ecommerce.order.catalog.ProductSnapshot;
import com.kora.ecommerce.order.catalog.ProductSnapshotResolutionException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrderCreationService {

    public static final int MAX_ORDER_ITEMS = 50;
    public static final int MAX_ITEM_QUANTITY = 100;

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.0000");

    private final CatalogProductClient catalogProductClient;
    private final OrderCreationPersistence orderCreationPersistence;
    private final Clock clock;

    OrderCreationService(
            CatalogProductClient catalogProductClient,
            OrderCreationPersistence orderCreationPersistence,
            Clock clock) {
        this.catalogProductClient = catalogProductClient;
        this.orderCreationPersistence = orderCreationPersistence;
        this.clock = clock;
    }

    public CreatedOrder createOrder(OrderCreationCommand command) {
        if (command == null || !StringUtils.hasText(command.customerId())) {
            throw OrderCreationException.authenticatedCustomerRequired();
        }

        List<OrderCreationItemCommand> requestedItems = command.items() == null
                ? List.of()
                : List.copyOf(command.items());
        validateRequestItems(requestedItems);

        List<OrderCreationDraftItem> draftItems = resolveDraftItems(requestedItems);
        String orderCurrency = validateSingleCurrency(draftItems);
        BigDecimal orderTotal = draftItems.stream()
                .map(OrderCreationDraftItem::lineTotalAmount)
                .reduce(ZERO_MONEY, BigDecimal::add)
                .setScale(4, RoundingMode.UNNECESSARY);

        OrderCreationDraft draft = new OrderCreationDraft(
                UUID.randomUUID(),
                command.customerId().trim(),
                orderTotal,
                orderTotal,
                orderCurrency,
                Instant.now(clock),
                draftItems);
        return orderCreationPersistence.persistCreatedOrder(draft);
    }

    private static void validateRequestItems(List<OrderCreationItemCommand> requestedItems) {
        if (requestedItems.isEmpty()) {
            throw OrderCreationException.emptyOrder();
        }
        if (requestedItems.size() > MAX_ORDER_ITEMS) {
            throw OrderCreationException.tooManyItems(MAX_ORDER_ITEMS);
        }

        Set<UUID> productIds = new HashSet<>();
        for (int index = 0; index < requestedItems.size(); index++) {
            OrderCreationItemCommand item = requestedItems.get(index);
            int itemNumber = index + 1;
            if (item == null || item.productId() == null) {
                throw OrderCreationException.missingProductId(itemNumber);
            }
            if (item.quantity() <= 0 || item.quantity() > MAX_ITEM_QUANTITY) {
                throw OrderCreationException.invalidQuantity(
                        item.productId(), item.quantity(), MAX_ITEM_QUANTITY);
            }
            if (!productIds.add(item.productId())) {
                throw OrderCreationException.duplicateProduct(item.productId());
            }
        }
    }

    private List<OrderCreationDraftItem> resolveDraftItems(List<OrderCreationItemCommand> requestedItems) {
        List<OrderCreationDraftItem> draftItems = new ArrayList<>(requestedItems.size());
        for (int index = 0; index < requestedItems.size(); index++) {
            OrderCreationItemCommand requestedItem = requestedItems.get(index);
            ProductSnapshot snapshot = catalogProductClient.resolveProductSnapshot(requestedItem.productId());
            if (snapshot == null) {
                throw ProductSnapshotResolutionException.malformed(requestedItem.productId());
            }
            BigDecimal unitPriceAmount = normalizeMoney(snapshot.unitPriceAmount(), snapshot.productId());
            BigDecimal lineTotal = unitPriceAmount
                    .multiply(BigDecimal.valueOf(requestedItem.quantity()))
                    .setScale(4, RoundingMode.UNNECESSARY);
            String currency = normalizeCurrency(snapshot.currency(), snapshot.productId());

            draftItems.add(new OrderCreationDraftItem(
                    index + 1,
                    snapshot.productId(),
                    snapshot.sku(),
                    snapshot.name(),
                    requestedItem.quantity(),
                    unitPriceAmount,
                    currency,
                    lineTotal));
        }
        return List.copyOf(draftItems);
    }

    private static String validateSingleCurrency(List<OrderCreationDraftItem> draftItems) {
        String currency = draftItems.getFirst().currency();
        for (OrderCreationDraftItem item : draftItems) {
            if (!currency.equals(item.currency())) {
                throw OrderCreationException.mixedCurrencies(item.productId());
            }
        }
        return currency;
    }

    private static BigDecimal normalizeMoney(BigDecimal amount, UUID productId) {
        try {
            return amount.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw ProductSnapshotResolutionException.malformed(productId, exception);
        }
    }

    private static String normalizeCurrency(String currency, UUID productId) {
        if (!StringUtils.hasText(currency)) {
            throw ProductSnapshotResolutionException.malformed(productId);
        }
        String normalizedCurrency = currency.trim();
        if (!normalizedCurrency.matches("[A-Z]{3}")) {
            throw ProductSnapshotResolutionException.malformed(productId);
        }
        return normalizedCurrency;
    }
}
