package com.example.orders;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final AuditLog auditLog;

    public OrderService(OrderRepository orderRepository, AuditLog auditLog) {
        this.orderRepository = orderRepository;
        this.auditLog = auditLog;
    }

    public Order placeOrder(NewOrder request) {
        Order order = persist(request);
        auditLog.recordPlacement(order.getId());
        return order;
    }

    @Transactional
    private Order persist(NewOrder request) {
        Order order = orderRepository.save(request.toEntity());
        order.markPlaced();
        return orderRepository.save(order);
    }
}
