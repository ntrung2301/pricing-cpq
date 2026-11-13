package com.cpq.app.catalog;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceAttributeValueRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.core.api.catalog.EntityInstancePageDto;
import com.cpq.core.api.catalog.EntityInstanceQueryService;
import com.cpq.core.api.catalog.EntityInstanceSummary;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityInstanceAttributeValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code app}-side implementation of {@code core-api}'s {@link
 * EntityInstanceQueryService} port (S2-T9-prereq, see docs/decisions.md
 * D-030), same interface-in-core-api/implementation-in-app split as {@code
 * EntityDefinitionQueryServiceImpl} (S1-T8) and {@code
 * RelationshipInstanceQueryServiceImpl} (S2-T7). Entirely generic: it has
 * no idea whether {@code entityDefCode} names a "Product", a "Vehicle", or
 * anything else — it only pages through {@link EntityInstance} rows by
 * entity_def code and, for each, loads whatever {@link
 * EntityInstanceAttributeValue} rows it has, exactly the way {@code
 * com.cpq.app.constraint.ConstraintInstanceEvaluationService} (S2-T2)
 * already does for the constraint engine.
 */
@Service
public class EntityInstanceQueryServiceImpl implements EntityInstanceQueryService {

    private final EntityDefinitionRepository entityDefinitionRepository;
    private final EntityInstanceRepository entityInstanceRepository;
    private final EntityInstanceAttributeValueRepository attributeValueRepository;

    public EntityInstanceQueryServiceImpl(
            EntityDefinitionRepository entityDefinitionRepository,
            EntityInstanceRepository entityInstanceRepository,
            EntityInstanceAttributeValueRepository attributeValueRepository) {
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.entityInstanceRepository = entityInstanceRepository;
        this.attributeValueRepository = attributeValueRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public EntityInstancePageDto listInstances(String entityDefCode, int page, int size, String search) {
        if (!entityDefinitionRepository.existsByCode(entityDefCode)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Unknown entity_def code: " + entityDefCode);
        }

        String normalizedSearch = normalize(search);
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<EntityInstance> result =
                normalizedSearch == null
                        ? entityInstanceRepository.findByEntityDefinition_Code(entityDefCode, pageable)
                        : entityInstanceRepository.searchByEntityDefinitionCode(
                                entityDefCode, normalizedSearch, pageable);

        return new EntityInstancePageDto(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private static String normalize(String search) {
        return (search == null || search.isBlank()) ? null : search.trim();
    }

    private EntityInstanceSummary toSummary(EntityInstance instance) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (EntityInstanceAttributeValue row : attributeValueRepository.findByEntityInstance(instance)) {
            attributes.put(row.getAttributeDefinition().getCode(), rawValue(row));
        }
        return new EntityInstanceSummary(
                instance.getId(), instance.getBusinessKey(), instance.getDisplayLabel(), attributes);
    }

    /**
     * Picks whichever single typed column is populated on {@code row}
     * (ADR-001's typed EAV guarantees exactly one is) — same logic as
     * {@code com.cpq.app.constraint.ConstraintInstanceEvaluationService#rawValue},
     * duplicated rather than extracted into a shared helper: both call
     * sites are small, independent, and (per ADR-005) belong to different
     * features reading the same generic EAV shape, not a bidirectional
     * coupling worth introducing a shared utility class for.
     */
    private static Object rawValue(EntityInstanceAttributeValue row) {
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
