package com.example.orders;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderAuditService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderAuditService.class);

    public void logCustomers(List<Order> orders) {
        orders.forEach(
                o -> LOG.info("order {} for {}", o.getId(), o.getCustomer().getDisplayName()));
    }

    public long totalLineCount(List<Order> orders) {
        return orders.stream().map(o -> o.getLines().size()).count();
    }
}
