package com.cpq.app.constraint;

import com.cpq.app.metamodel.EntityInstanceAttributeValueRepository;
import com.cpq.core.constraintengine.AttributeBag;
import com.cpq.core.constraintengine.ConstraintEngine;
import com.cpq.core.constraintengine.ConstraintEvaluationResult;
import com.cpq.core.constraintengine.EvaluationContext;
import com.cpq.core.metamodel.ConstraintInstance;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityInstanceAttributeValue;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates a {@link ConstraintInstance} against the current attribute
 * values of its two {@link EntityInstance}s (E1-02, S2-T2). Generic — it
 * has no idea whether X/Y are "Part"/"Vehicle" or anything else; it only
 * loads whatever {@link EntityInstanceAttributeValue} rows exist for each
 * side, maps them to the raw Java value shapes
 * {@code core-constraint-engine}'s {@link AttributeBag} understands, and
 * delegates the actual boolean evaluation to {@link ConstraintEngine}
 * (which lives in {@code core-constraint-engine} and knows nothing about
 * JPA/the database — this service is the glue between the two, per
 * ADR-005: {@code app} owns DB-facing wiring, core-* stays framework/DB
 * free).
 */
@Service
public class ConstraintInstanceEvaluationService {

    private final EntityInstanceAttributeValueRepository attributeValueRepository;
    private final ConstraintEngine constraintEngine = new ConstraintEngine();

    public ConstraintInstanceEvaluationService(
            EntityInstanceAttributeValueRepository attributeValueRepository) {
        this.attributeValueRepository = attributeValueRepository;
    }

    @Transactional(readOnly = true)
    public ConstraintEvaluationResult evaluate(ConstraintInstance constraintInstance) {
        AttributeBag x = loadAttributeBag(constraintInstance.getEntityInstanceX());
        AttributeBag y = loadAttributeBag(constraintInstance.getEntityInstanceY());
        EvaluationContext context = EvaluationContext.of(Map.of("x", x, "y", y));
        return constraintEngine.evaluate(constraintInstance.getCondition(), context);
    }

    private AttributeBag loadAttributeBag(EntityInstance instance) {
        AttributeBag.Builder builder = AttributeBag.builder();
        for (EntityInstanceAttributeValue row : attributeValueRepository.findByEntityInstance(instance)) {
            builder.put(row.getAttributeDefinition().getCode(), rawValue(row));
        }
        return builder.build();
    }

    /**
     * Picks whichever single typed column is populated on {@code row}
     * (ADR-001's typed EAV guarantees exactly one is), matching the raw
     * Java value shapes {@code AttributeBag} expects.
     */
    private Object rawValue(EntityInstanceAttributeValue row) {
        if (row.getValueString() != null) {
            return row.getValueString();
        }
        if (row.getValueNumber() != null) {
            return row.getValueNumber();
        }
        if (row.getValueDate() != null) {
            return row.getValueDate();
        }
        if (row.getValueBoolean() != null) {
            return row.getValueBoolean();
        }
        return row.getValueRefInstanceId();
    }
}
