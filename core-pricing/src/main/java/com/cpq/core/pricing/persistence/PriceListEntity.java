package com.cpq.core.pricing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA row for {@code price_list} (ADR-003 §"Price list"). */
@Entity
@Table(name = "price_list")
public class PriceListEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    protected PriceListEntity() {
        // JPA
    }

    public PriceListEntity(String code, String name, String currency) {
        this.code = code;
        this.name = name;
        this.currency = currency;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }
}
