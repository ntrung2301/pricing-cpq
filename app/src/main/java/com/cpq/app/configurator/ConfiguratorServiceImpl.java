package com.cpq.app.configurator;

import com.cpq.app.constraint.ConstraintInstanceEvaluationService;
import com.cpq.app.constraint.ConstraintInstanceRepository;
import com.cpq.core.api.configurator.CompatibleInstanceSummary;
import com.cpq.core.api.configurator.ConfiguratorService;
import com.cpq.core.api.configurator.ConstraintViolationSummary;
import com.cpq.core.api.configurator.ValidateSelectionRequest;
import com.cpq.core.api.configurator.ValidateSelectionResponse;
import com.cpq.core.constraintengine.ConstraintEvaluationResult;
import com.cpq.core.metamodel.ConstraintInstance;
import com.cpq.core.metamodel.EntityInstance;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code app}-side implementation of {@code core-api}'s
 * {@link ConfiguratorService} port (S2-T8, E2-01), same
 * interface-in-core-api/implementation-in-app split as
 * {@code EntityDefinitionQueryServiceImpl} (S1-T8) and
 * {@code PricingCalculationServiceImpl} (S2-T10).
 *
 * <p>Entirely generic: it reads {@link ConstraintInstance} rows and
 * delegates the actual boolean evaluation to
 * {@link ConstraintInstanceEvaluationService} (S2-T2) — it never looks at
 * what entity_def X or Y actually are. This class owns no business logic
 * of its own beyond "which constraint_instance rows are relevant" and
 * "turn a failing one into a human-readable message" (per D-024's note
 * that {@link ConstraintEvaluationResult} is deliberately just a
 * boolean — that explanation is this API's job).
 */
@Service
public class ConfiguratorServiceImpl implements ConfiguratorService {

    private static final String COMPATIBILITY_TYPE = "compatibility";
    private static final int MINIMUM_SELECTION_SIZE_FOR_ANY_PAIR = 2;

    private final ConstraintInstanceRepository constraintInstanceRepository;
    private final ConstraintInstanceEvaluationService constraintInstanceEvaluationService;

    public ConfiguratorServiceImpl(
            ConstraintInstanceRepository constraintInstanceRepository,
            ConstraintInstanceEvaluationService constraintInstanceEvaluationService) {
        this.constraintInstanceRepository = constraintInstanceRepository;
        this.constraintInstanceEvaluationService = constraintInstanceEvaluationService;
    }

    @Override
    @Transactional(readOnly = true)
    public ValidateSelectionResponse validateSelection(ValidateSelectionRequest request) {
        List<Long> entityInstanceIds =
                request.entityInstanceIds() == null ? List.of() : request.entityInstanceIds();
        if (entityInstanceIds.size() < MINIMUM_SELECTION_SIZE_FOR_ANY_PAIR) {
            // No pair exists within the selection, so no constraint_instance can
            // possibly have both its endpoints in it.
            return new ValidateSelectionResponse(true, List.of());
        }

        List<ConstraintInstance> relevantConstraints =
                constraintInstanceRepository.findBetweenAnyOf(entityInstanceIds);

        List<ConstraintViolationSummary> violations = new ArrayList<>();
        for (ConstraintInstance constraintInstance : relevantConstraints) {
            ConstraintEvaluationResult result = constraintInstanceEvaluationService.evaluate(constraintInstance);
            if (!result.passed()) {
                violations.add(toViolationSummary(constraintInstance));
            }
        }
        return new ValidateSelectionResponse(violations.isEmpty(), violations);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompatibleInstanceSummary> listCompatibleInstances(
            Long anchorInstanceId, String targetEntityDefCode) {
        List<ConstraintInstance> candidates =
                constraintInstanceRepository.findByTypeAndEitherEndpoint(
                        COMPATIBILITY_TYPE, anchorInstanceId);

        List<CompatibleInstanceSummary> compatibleInstances = new ArrayList<>();
        for (ConstraintInstance constraintInstance : candidates) {
            EntityInstance other = otherSide(constraintInstance, anchorInstanceId);
            if (!other.getEntityDefinition().getCode().equals(targetEntityDefCode)) {
                continue;
            }
            ConstraintEvaluationResult result = constraintInstanceEvaluationService.evaluate(constraintInstance);
            if (result.passed()) {
                compatibleInstances.add(
                        new CompatibleInstanceSummary(
                                other.getId(), other.getBusinessKey(), other.getDisplayLabel()));
            }
        }
        return compatibleInstances;
    }

    private static EntityInstance otherSide(ConstraintInstance constraintInstance, Long anchorInstanceId) {
        return constraintInstance.getEntityInstanceX().getId().equals(anchorInstanceId)
                ? constraintInstance.getEntityInstanceY()
                : constraintInstance.getEntityInstanceX();
    }

    private static ConstraintViolationSummary toViolationSummary(ConstraintInstance constraintInstance) {
        Long constraintDefId = constraintInstance.getId();
        String code = constraintInstance.getConstraintDefinition().getCode();
        String type = constraintInstance.getConstraintDefinition().getType();
        Long xId = constraintInstance.getEntityInstanceX().getId();
        Long yId = constraintInstance.getEntityInstanceY().getId();
        String message =
                "Constraint '"
                        + code
                        + "' (type="
                        + type
                        + ") failed between entity_instance "
                        + xId
                        + " and entity_instance "
                        + yId;
        return new ConstraintViolationSummary(constraintDefId, code, type, xId, yId, message);
    }
}
