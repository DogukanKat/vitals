package com.example;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {

    @KafkaListener(topics = "orders", groupId = "orders")
    public void onOrder(String payload) {
        process(payload);
    }

    private void process(String payload) {
        // business logic that may fail or be slow
    }
}
