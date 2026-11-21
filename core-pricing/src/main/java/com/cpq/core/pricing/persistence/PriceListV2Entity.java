package com.cpq.core.pricing.persistence;

import com.cpq.core.pricing.dag.DagNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA row for {@code price_list_v2} (S3-T6, ADR-009 mục 5, backlog E8-05):
 * a Price List whose price is computed by running {@code
 * calculation_graph} (a {@code List<DagNode>}, same representation/{@code
 * jsonb} storage as {@code measure_definition.graph} — reuses {@link
 * DagNodeGraphConverter} directly, per docs/decisions.md D-045 point 3's
 * forward note) through {@code
 * com.cpq.app.pricing.dag.MeasureAwareDagEngine}, once per {@link
 * PriceListItemV2Entity}.
 *
 * <p><b>Naming</b>: this is NOT the same table/concept as the Sprint 2
 * {@code RuleEngine}-based {@link PriceListEntity}/{@code price_list}
 * (S2-T10) — that table/engine is untouched, running in parallel per
 * sprint-3.md's "NEEDS-PO-REVIEW đang mở" note (no migration decision
 * made yet). The {@code V2} suffix on this class (and {@link
 * PriceListItemV2Entity}, {@link PriceListV2Status}) exists purely to
 * avoid colliding with the old class names already present in this exact
 * package — see docs/decisions.md D-046.
 *
 * <p>Deliberately dumb data plus a few narrow, well-named mutation methods
 * (not a rich domain object) — same balance {@code EntityInstance} strikes
 * with {@code syncBusinessKey}/{@code assignAnchorInstance}: the status
 * state machine is small and co-located with the field it guards, but the
 * broader orchestration (loading the entity, running the DAG, saving)
 * stays in {@code com.cpq.app.pricing.pricelist.PriceListServiceImpl}.
 */
@Entity
@Table(name = "price_list_v2")
public class PriceListV2Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PriceListV2Status status;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Convert(converter = DagNodeGraphConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_graph", nullable = false, columnDefinition = "jsonb")
    private List<DagNode> calculationGraph;

    @Column(name = "result_node_id", nullable = false, length = 100)
    private String resultNodeId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected PriceListV2Entity() {
        // JPA
    }

    public PriceListV2Entity(
            String code,
            String name,
            String currency,
            LocalDate validFrom,
            LocalDate validTo,
            List<DagNode> calculationGraph,
            String resultNodeId,
            String createdBy) {
        this.code = code;
        this.name = name;
        this.currency = currency;
        this.status = PriceListV2Status.DRAFT;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.calculationGraph = calculationGraph;
        this.resultNodeId = resultNodeId;
        this.version = 1;
        this.createdBy = createdBy;
    }

    /**
     * {@code DRAFT -> PENDING_APPROVAL}. Throws {@link
     * PriceListV2StateException} for any other current status (ADR-009
     * mục 5's state machine is not skippable/re-enterable).
     */
    public void submit() {
        if (status != PriceListV2Status.DRAFT) {
            throw new PriceListV2StateException(
                    "Price list '" + code + "' is " + status + ", not DRAFT — cannot submit");
        }
        status = PriceListV2Status.PENDING_APPROVAL;
        submittedAt = Instant.now();
    }

    /**
     * {@code PENDING_APPROVAL -> APPROVED}. From this point on, {@link
     * #requireMutable()} blocks every item add/recompute — ADR-009 mục 5's
     * "snapshot vĩnh viễn".
     */
    public void approve(String approvedBy) {
        if (status != PriceListV2Status.PENDING_APPROVAL) {
            throw new PriceListV2StateException(
                    "Price list '" + code + "' is " + status + ", not PENDING_APPROVAL — cannot approve");
        }
        status = PriceListV2Status.APPROVED;
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
    }

    /**
     * Guard called by {@code PriceListServiceImpl.addItem(...)} before
     * running the DAG for a new/updated item: only {@code DRAFT}/{@code
     * PENDING_APPROVAL} price lists may have items added/recomputed.
     * {@code APPROVED} (and anything reached from it later — {@code
     * ACTIVE}/{@code EXPIRED}) is a permanent snapshot (ADR-009 mục 5);
     * {@code REJECTED} is likewise a dead end.
     */
    public void requireMutable() {
        if (status != PriceListV2Status.DRAFT && status != PriceListV2Status.PENDING_APPROVAL) {
            throw new PriceListV2StateException(
                    "Price list '"
                            + code
                            + "' is "
                            + status
                            + " — its items are a permanent snapshot, cannot add or recompute");
        }
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

    public PriceListV2Status getStatus() {
        return status;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public List<DagNode> getCalculationGraph() {
        return calculationGraph;
    }

    public String getResultNodeId() {
        return resultNodeId;
    }

    public int getVersion() {
        return version;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
