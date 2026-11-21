package com.cpq.core.pricing.persistence;

import com.cpq.core.pricing.dag.DagNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA row for {@code measure_definition} (ADR-009 mục 2 "Measure", S3-T5,
 * backlog E8-04): a named, reusable DAG subgraph (a "measure" in the DAX
 * sense — defined once, parameterized by whatever "context"/anchor
 * instance the caller is evaluating it for, never a hardcoded instance
 * inside the definition itself). {@code graph} holds the subgraph's {@link
 * DagNode} list verbatim, as {@code jsonb}; {@code resultNodeId} names
 * which of those nodes' output is "the" value this measure resolves to
 * when referenced via a {@code measure-ref} {@link DagNode} (see {@code
 * com.cpq.app.pricing.dag.MeasureRefNodeExecutor}).
 *
 * <p>Plain {@code jakarta.persistence} class in {@code core-pricing} — same
 * ADR-005 split as {@code CalculationBindingEntity}: {@code app} owns the
 * repository.
 */
@Entity
@Table(name = "measure_definition")
public class MeasureDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Convert(converter = DagNodeGraphConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "graph", nullable = false, columnDefinition = "jsonb")
    private List<DagNode> graph;

    @Column(name = "result_node_id", nullable = false, length = 100)
    private String resultNodeId;

    protected MeasureDefinitionEntity() {
        // JPA
    }

    public MeasureDefinitionEntity(String code, String name, List<DagNode> graph, String resultNodeId) {
        this.code = code;
        this.name = name;
        this.graph = graph;
        this.resultNodeId = resultNodeId;
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

    public List<DagNode> getGraph() {
        return graph;
    }

    public String getResultNodeId() {
        return resultNodeId;
    }
}
