package com.example.orders;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.time.Instant;

@Entity
public class Order {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    private Customer customer;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    private Invoice invoice;

    private Instant placedAt;

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }
}
