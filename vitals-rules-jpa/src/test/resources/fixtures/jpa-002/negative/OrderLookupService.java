package com.example.orders;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderLookupService {

    private final OrderRepository orderRepository;

    public OrderLookupService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public String customerNameFor(Long orderId) {
        Order order = orderRepository.findWithCustomer(orderId).orElseThrow();
        return order.getCustomer().getDisplayName();
    }
}
