package com.example.orders;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.List;
import java.util.Set;

@Entity
public class Order {

    @Id
    private Long id;

    // Marker annotation: no fetch attribute, defaults are out of scope for JPA-001.
    @ManyToOne
    private Customer customer;

    // Should fire: ManyToMany explicit EAGER.
    @ManyToMany(fetch = FetchType.EAGER)
    private Set<Tag> tags;

    // OneToMany defaults to LAZY; explicit LAZY must not fire.
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "order")
    private List<OrderLine> lines;
}
