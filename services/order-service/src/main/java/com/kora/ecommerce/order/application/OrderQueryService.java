package com.kora.ecommerce.order.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.kora.ecommerce.order.persistence.OrderEntity;
import com.kora.ecommerce.order.persistence.OrderItemEntity;
import com.kora.ecommerce.order.persistence.OrderItemRepository;
import com.kora.ecommerce.order.persistence.OrderRepository;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryEntity;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderQueryService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    OrderQueryService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
    }

    @Transactional(readOnly = true)
    public QueriedOrderPage listCustomerOrders(String customerId, Integer page, Integer size) {
        String normalizedCustomerId = normalizeCustomerId(customerId);
        int pageNumber = normalizePage(page);
        int pageSize = normalizeSize(size);

        Page<OrderEntity> orderPage = orderRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(
                normalizedCustomerId,
                PageRequest.of(pageNumber, pageSize));
        List<OrderEntity> orders = orderPage.getContent();
        HydratedOrderChildren children = loadChildren(orders.stream()
                .map(OrderEntity::getId)
                .toList());

        return new QueriedOrderPage(
                orderPage.getNumber(),
                orderPage.getSize(),
                orderPage.getTotalElements(),
                orderPage.getTotalPages(),
                orderPage.hasNext(),
                orders.stream()
                        .map(order -> toQueriedOrder(order, children))
                        .toList());
    }

    @Transactional(readOnly = true)
    public QueriedOrder getCustomerOrder(String customerId, UUID orderId) {
        String normalizedCustomerId = normalizeCustomerId(customerId);
        if (orderId == null) {
            throw OrderQueryException.orderIdRequired();
        }
        OrderEntity order = orderRepository.findByIdAndCustomerId(orderId, normalizedCustomerId)
                .orElseThrow(() -> OrderQueryException.orderNotFound(orderId));
        return toQueriedOrder(order, loadChildren(List.of(orderId)));
    }

    @Transactional(readOnly = true)
    public QueriedOrder getOrderForOperations(UUID orderId) {
        if (orderId == null) {
            throw OrderQueryException.orderIdRequired();
        }
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderQueryException.orderNotFound(orderId));
        return toQueriedOrder(order, loadChildren(List.of(orderId)));
    }

    private static String normalizeCustomerId(String customerId) {
        if (!StringUtils.hasText(customerId)) {
            throw OrderQueryException.authenticatedCustomerRequired();
        }
        return customerId.trim();
    }

    private static int normalizePage(Integer page) {
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw OrderQueryException.invalidPageNumber();
        }
        return pageNumber;
    }

    private static int normalizeSize(Integer size) {
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageSize <= 0) {
            throw OrderQueryException.invalidPageSize();
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw OrderQueryException.pageSizeTooLarge(MAX_PAGE_SIZE);
        }
        return pageSize;
    }

    private HydratedOrderChildren loadChildren(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return new HydratedOrderChildren(Map.of(), Map.of());
        }

        Map<UUID, List<OrderItemEntity>> itemsByOrderId = orderItemRepository.findForOrdersOrdered(orderIds).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getOrder().getId(),
                        Collectors.toList()));
        Map<UUID, List<OrderStatusHistoryEntity>> historyByOrderId =
                orderStatusHistoryRepository.findForOrdersOrdered(orderIds).stream()
                        .collect(Collectors.groupingBy(
                                history -> history.getOrder().getId(),
                                Collectors.toList()));
        return new HydratedOrderChildren(itemsByOrderId, historyByOrderId);
    }

    private static QueriedOrder toQueriedOrder(OrderEntity order, HydratedOrderChildren children) {
        List<QueriedOrder.Item> items = children.itemsByOrderId()
                .getOrDefault(order.getId(), List.of())
                .stream()
                .map(item -> new QueriedOrder.Item(
                        item.getItemNumber(),
                        item.getProductId(),
                        item.getProductSku(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPriceAmount(),
                        item.getCurrency(),
                        item.getLineTotalAmount()))
                .toList();
        List<QueriedOrder.StatusHistory> statusHistory = children.historyByOrderId()
                .getOrDefault(order.getId(), List.of())
                .stream()
                .map(history -> new QueriedOrder.StatusHistory(
                        history.getStatus(),
                        history.getChangedAt(),
                        history.getReason()))
                .toList();

        return new QueriedOrder(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getSubtotalAmount(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items,
                statusHistory);
    }

    private record HydratedOrderChildren(
            Map<UUID, List<OrderItemEntity>> itemsByOrderId,
            Map<UUID, List<OrderStatusHistoryEntity>> historyByOrderId) {
    }
}
