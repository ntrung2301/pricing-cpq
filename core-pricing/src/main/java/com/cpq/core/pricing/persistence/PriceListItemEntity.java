package com.cpq.core.pricing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JPA row for {@code price_list_item} (ADR-003 §"Price list"). {@code
 * entityDefId}/{@code entityInstanceId} are plain {@code Long} columns,
 * not a JPA {@code @ManyToOne} to core-metamodel's {@code EntityDefinition}/
 * {@code EntityInstance} — core-pricing deliberately does not depend on
 * core-metamodel (see docs/decisions.md S2-T10 entry); the DB-level
 * foreign key (declared in the Flyway migration) is enough referential
 * integrity, and the app layer resolves the id when it needs the actual
 * entity_def/entity_instance row. {@code entityInstanceId} is nullable:
 * {@code null} means this item applies by segment/condition rather than
 * to one specific instance (ADR-003).
 */
@Entity
@Table(name = "price_list_item")
public class PriceListItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_list_id", nullable = false)
    private PriceListEntity priceList;

    @Column(name = "entity_def_id", nullable = false)
    private Long entityDefId;

    @Column(name = "entity_instance_id")
    private Long entityInstanceId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    protected PriceListItemEntity() {
        // JPA
    }

    public PriceListItemEntity(
            PriceListEntity priceList,
            Long entityDefId,
            Long entityInstanceId,
            BigDecimal amount,
            LocalDate validFrom,
            LocalDate validTo) {
        this.priceList = priceList;
        this.entityDefId = entityDefId;
        this.entityInstanceId = entityInstanceId;
        this.amount = amount;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public Long getId() {
        return id;
    }

    public PriceListEntity getPriceList() {
        return priceList;
    }

    public Long getEntityDefId() {
        return entityDefId;
    }

    public Long getEntityInstanceId() {
        return entityInstanceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }
}
