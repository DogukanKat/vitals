package com.example;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {

    @KafkaListener(topics = "orders", groupId = "orders")
    public void onOrder(String payload, Acknowledgment ack) {
        process(payload);
        ack.acknowledge();
    }

    private void process(String payload) {
        // business logic
    }
}
