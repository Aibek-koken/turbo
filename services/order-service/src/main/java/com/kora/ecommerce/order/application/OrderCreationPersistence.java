package com.kora.ecommerce.order.application;

import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderEntity;
import com.kora.ecommerce.order.persistence.OrderItemEntity;
import com.kora.ecommerce.order.persistence.OrderRepository;
import com.kora.ecommerce.order.persistence.OrderStatus;
import com.kora.ecommerce.order.persistence.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCreationPersistence {

    private static final String CREATED_REASON = "order created";

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderCreatedOutboxEventFactory orderCreatedOutboxEventFactory;

    OrderCreationPersistence(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            OrderCreatedOutboxEventFactory orderCreatedOutboxEventFactory) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.orderCreatedOutboxEventFactory = orderCreatedOutboxEventFactory;
    }

    @Transactional
    public CreatedOrder persistCreatedOrder(OrderCreationDraft draft) {
        OrderEntity order = OrderEntity.create(
                draft.orderId(),
                draft.customerId(),
                draft.subtotalAmount(),
                draft.totalAmount(),
                draft.currency(),
                draft.createdAt());

        for (OrderCreationDraftItem item : draft.items()) {
            order.addItem(OrderItemEntity.snapshot(
                    UUID.randomUUID(),
                    item.itemNumber(),
                    item.productId(),
                    item.productSku(),
                    item.productName(),
                    item.quantity(),
                    item.unitPriceAmount(),
                    item.currency(),
                    item.lineTotalAmount()));
        }
        order.appendStatusHistory(OrderStatus.CREATED, draft.createdAt(), CREATED_REASON);

        OrderEntity saved = orderRepository.saveAndFlush(order);
        outboxEventRepository.saveAndFlush(orderCreatedOutboxEventFactory.createEvent(draft));
        return toCreatedOrder(saved);
    }

    private static CreatedOrder toCreatedOrder(OrderEntity order) {
        List<CreatedOrderItem> items = order.getItems().stream()
                .map(item -> new CreatedOrderItem(
                        item.getItemNumber(),
                        item.getProductId(),
                        item.getProductSku(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPriceAmount(),
                        item.getCurrency(),
                        item.getLineTotalAmount()))
                .toList();

        return new CreatedOrder(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getSubtotalAmount(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                items);
    }
}
