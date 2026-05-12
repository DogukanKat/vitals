package com.example.orders;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSummaryService {

    private final OrderRepository orderRepository;

    public OrderSummaryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public String summarize() {
        List<Order> orders = orderRepository.findAll();
        StringBuilder out = new StringBuilder();
        for (Order order : orders) {
            out.append(order.getCustomer().getDisplayName()).append('\n');
        }
        return out.toString();
    }
}
