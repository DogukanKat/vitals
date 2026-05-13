package com.example.orders;

import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;

    public OrderService(OrderRepository orderRepository, RestTemplate restTemplate) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public Order placeOrder(NewOrder request) throws InterruptedException {
        Order order = orderRepository.save(request.toEntity());
        TaxQuote quote = restTemplate.getForObject("/tax/" + order.getId(), TaxQuote.class);
        Thread.sleep(TimeUnit.MILLISECONDS.toMillis(50));
        order.applyTax(quote);
        return orderRepository.save(order);
    }
}
