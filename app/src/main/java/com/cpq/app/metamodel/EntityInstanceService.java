package com.cpq.app.metamodel;

import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityInstanceAttributeValue;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates {@link EntityInstance}s and writes their
 * {@link EntityInstanceAttributeValue}s (S2-T1, ADR-001's typed EAV).
 * Entirely generic — it has no idea whether the caller is writing a
 * "Part", a "Customer", or anything else.
 *
 * <p>Per D-019, whenever the attribute being written is the owning
 * {@link EntityDefinition}'s {@code keyAttributeDefinition} or
 * {@code labelAttributeDefinition}, this service also synchronizes the
 * instance's denormalized {@code businessKey}/{@code displayLabel} — in
 * Java, not a DB trigger, so the sync is unit/integration-testable and
 * auditable. Duplicate business keys are rejected by the database's
 * unique index ({@code uq_entity_instance_def_pack_business_key}), which
 * surfaces as a {@code DataIntegrityViolationException} when the instance
 * is flushed.
 */
@Service
public class EntityInstanceService {

    private final EntityInstanceRepository entityInstanceRepository;
    private final EntityInstanceAttributeValueRepository attributeValueRepository;

    public EntityInstanceService(
            EntityInstanceRepository entityInstanceRepository,
            EntityInstanceAttributeValueRepository attributeValueRepository) {
        this.entityInstanceRepository = entityInstanceRepository;
        this.attributeValueRepository = attributeValueRepository;
    }

    @Transactional
    public EntityInstance createInstance(EntityDefinition entityDefinition, String packId, int packVersion) {
        return entityInstanceRepository.save(new EntityInstance(entityDefinition, packId, packVersion));
    }

    /**
     * Writes (or overwrites) {@code attribute}'s value for {@code instance},
     * validating that {@code value}'s shape matches
     * {@code attribute.getDataType()} first. If {@code attribute} is the
     * instance's entity definition's key/label attribute, also
     * synchronizes {@code businessKey}/{@code displayLabel} (D-019) and
     * re-saves the instance so a duplicate business key fails fast on
     * flush.
     */
    @Transactional
    public EntityInstanceAttributeValue writeAttributeValue(
            EntityInstance instance, AttributeDefinition attribute, AttributeValue value) {
        if (!value.matches(attribute.getDataType())) {
            throw new AttributeValueTypeMismatchException(attribute.getCode(), attribute.getDataType());
        }

        EntityInstanceAttributeValue row =
                attributeValueRepository
                        .findByEntityInstanceAndAttributeDefinition(instance, attribute)
                        .map(
                                existing -> {
                                    existing.applyValue(value);
                                    return existing;
                                })
                        .orElseGet(() -> new EntityInstanceAttributeValue(instance, attribute, value));
        EntityInstanceAttributeValue saved = attributeValueRepository.save(row);

        syncKeyOrLabelIfApplicable(instance, attribute, value);

        return saved;
    }

    private void syncKeyOrLabelIfApplicable(
            EntityInstance instance, AttributeDefinition attribute, AttributeValue value) {
        EntityDefinition entityDefinition = instance.getEntityDefinition();
        if (isSameAttribute(attribute, entityDefinition.getKeyAttributeDefinition())) {
            instance.syncBusinessKey(value.asDisplayString());
            entityInstanceRepository.save(instance);
        }
        if (isSameAttribute(attribute, entityDefinition.getLabelAttributeDefinition())) {
            instance.syncDisplayLabel(value.asDisplayString());
            entityInstanceRepository.save(instance);
        }
    }

    private static boolean isSameAttribute(AttributeDefinition a, AttributeDefinition b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }
}
