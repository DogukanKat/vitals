package com.example.orders;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order placeOrder(NewOrder request) {
        Order order = orderRepository.save(request.toEntity());
        order.markPlaced();
        return orderRepository.save(order);
    }
}
