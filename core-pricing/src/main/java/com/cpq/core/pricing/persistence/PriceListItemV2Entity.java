package com.cpq.core.pricing.persistence;

import com.cpq.core.pricing.dag.TraceEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA row for {@code price_list_item_v2} (S3-T6, ADR-009 mục 5): one
 * {@code Product} (core-metamodel {@code EntityInstance}) priced within a
 * {@link PriceListV2Entity}. {@code resultPrice}/{@code waterfallSteps}
 * are computed exactly once, when the item is added (by {@code
 * com.cpq.app.pricing.pricelist.PriceListServiceImpl.addItem(...)} running
 * {@code calculation_graph} through {@code
 * com.cpq.app.pricing.dag.MeasureAwareDagEngine}) — see {@link
 * #recordCalculationResult}. {@code manualOverridePrice} (see {@link
 * #setManualOverridePrice}) is the ONLY field any API in this codebase can
 * change afterwards (ADR-009 mục 5: "mọi field khác... cố định").
 *
 * <p>{@code entityInstanceId} is a plain {@code Long}, not a JPA {@code
 * @ManyToOne} to core-metamodel's {@code EntityInstance} — same
 * deliberate choice, for the same reason, as the old engine's {@code
 * PriceListItemEntity} (S2-T10): {@code core-pricing} does not depend on
 * {@code core-metamodel} (ADR-005). The database-level foreign key (see
 * the {@code V12} Flyway migration) is enough referential integrity;
 * {@code app} resolves the id to a real {@code EntityInstance} when it
 * needs one (e.g. to inject it as the DAG's anchor).
 */
@Entity
@Table(name = "price_list_item_v2")
public class PriceListItemV2Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_list_id", nullable = false)
    private PriceListV2Entity priceList;

    @Column(name = "entity_instance_id", nullable = false)
    private Long entityInstanceId;

    @Column(name = "result_price", precision = 19, scale = 4)
    private BigDecimal resultPrice;

    @Column(name = "manual_override_price", precision = 19, scale = 4)
    private BigDecimal manualOverridePrice;

    @Convert(converter = TraceEntryListConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "waterfall_steps", columnDefinition = "jsonb")
    private List<TraceEntry> waterfallSteps;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    protected PriceListItemV2Entity() {
        // JPA
    }

    public PriceListItemV2Entity(
            PriceListV2Entity priceList, Long entityInstanceId, LocalDate validFrom, LocalDate validTo) {
        this.priceList = priceList;
        this.entityInstanceId = entityInstanceId;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    /**
     * The ONLY mutation point for {@code resultPrice}/{@code
     * waterfallSteps} — intended to be called exactly once, by {@code
     * PriceListServiceImpl.addItem(...)}, right after running the DAG for
     * this item. Never exposed via any PATCH/PUT endpoint (S3-T6
     * Done-khi: "mọi field khác (bao gồm result_price) không có endpoint
     * PATCH/PUT nào cho phép sửa trực tiếp").
     */
    public void recordCalculationResult(BigDecimal resultPrice, List<TraceEntry> waterfallSteps) {
        this.resultPrice = resultPrice;
        this.waterfallSteps = waterfallSteps;
    }

    /**
     * The ONE field an API may change after creation (ADR-009 mục 5) —
     * {@code PATCH /api/price-lists-v2/items/{itemId}/override}, via
     * {@code PriceListServiceImpl.overridePrice(...)}. Deliberately
     * unguarded by the price list's own status (DRAFT or APPROVED): an
     * override is "a layer on top", never a recompute.
     */
    public void setManualOverridePrice(BigDecimal manualOverridePrice) {
        this.manualOverridePrice = manualOverridePrice;
    }

    public Long getId() {
        return id;
    }

    public PriceListV2Entity getPriceList() {
        return priceList;
    }

    public Long getEntityInstanceId() {
        return entityInstanceId;
    }

    public BigDecimal getResultPrice() {
        return resultPrice;
    }

    public BigDecimal getManualOverridePrice() {
        return manualOverridePrice;
    }

    public List<TraceEntry> getWaterfallSteps() {
        return waterfallSteps;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }
}
