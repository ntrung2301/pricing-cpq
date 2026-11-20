package com.cpq.app.metamodel;

import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityDefinitionDimensionAttribute;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityInstanceAttributeValue;
import com.cpq.core.metamodel.EntityRole;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 *
 * <p><b>Entity taxonomy v2 (ADR-009 mục 1, D-037/D-038/D-039, S3-T1):</b>
 * for an instance of an {@link EntityRole#EXTENSION} entity definition,
 * {@code businessKey} is instead assembled from the anchor instance's own
 * {@code businessKey} plus the ordered dimension attribute values
 * (separator {@code "|"}, see {@link #BUSINESS_KEY_SEPARATOR}) — see
 * {@link #createInstance(EntityDefinition, String, int, Long)} and
 * {@link #recomputeExtensionBusinessKeyIfComplete}.
 */
@Service
public class EntityInstanceService {

    /**
     * Separator joining an {@link EntityRole#EXTENSION} instance's
     * {@code businessKey} parts: the anchor instance's own {@code
     * businessKey}, then each dimension attribute value in {@code
     * entity_def_dimension_attribute.position} order (D-039). Chosen
     * because none of the 8 generic {@code AttributeDataType} display
     * forms (ADR-001) can ever contain it.
     */
    static final String BUSINESS_KEY_SEPARATOR = "|";

    private final EntityInstanceRepository entityInstanceRepository;
    private final EntityInstanceAttributeValueRepository attributeValueRepository;
    private final EntityDefinitionDimensionAttributeRepository dimensionAttributeRepository;

    public EntityInstanceService(
            EntityInstanceRepository entityInstanceRepository,
            EntityInstanceAttributeValueRepository attributeValueRepository,
            EntityDefinitionDimensionAttributeRepository dimensionAttributeRepository) {
        this.entityInstanceRepository = entityInstanceRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.dimensionAttributeRepository = dimensionAttributeRepository;
    }

    @Transactional
    public EntityInstance createInstance(EntityDefinition entityDefinition, String packId, int packVersion) {
        return createInstance(entityDefinition, packId, packVersion, null);
    }

    /**
     * Same as {@link #createInstance(EntityDefinition, String, int)}, but
     * accepts {@code anchorInstanceId} (D-039): required (clear {@link
     * IllegalArgumentException} otherwise) when {@code
     * entityDefinition.getRole() == EntityRole.EXTENSION}, ignored
     * otherwise (not an error to pass one for a non-Extension entity —
     * simply has no effect, per S3-T1 scope: "bỏ qua/null nếu không phải
     * Extension").
     */
    @Transactional
    public EntityInstance createInstance(
            EntityDefinition entityDefinition, String packId, int packVersion, Long anchorInstanceId) {
        EntityInstance instance = new EntityInstance(entityDefinition, packId, packVersion);

        if (entityDefinition.getRole() == EntityRole.EXTENSION) {
            if (anchorInstanceId == null) {
                throw new IllegalArgumentException(
                        "Entity definition '"
                                + entityDefinition.getCode()
                                + "' has role EXTENSION — creating an instance requires an anchor instance id");
            }
            EntityInstance anchorInstance =
                    entityInstanceRepository
                            .findById(anchorInstanceId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Anchor instance id " + anchorInstanceId + " does not exist"));
            instance.assignAnchorInstance(anchorInstance);
        }

        return entityInstanceRepository.save(instance);
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

        if (entityDefinition.getRole() == EntityRole.EXTENSION) {
            recomputeExtensionBusinessKeyIfComplete(instance, entityDefinition, attribute);
        } else if (isSameAttribute(attribute, entityDefinition.getKeyAttributeDefinition())) {
            instance.syncBusinessKey(value.asDisplayString());
            entityInstanceRepository.save(instance);
        }

        if (isSameAttribute(attribute, entityDefinition.getLabelAttributeDefinition())) {
            instance.syncDisplayLabel(value.asDisplayString());
            entityInstanceRepository.save(instance);
        }
    }

    /**
     * D-039: whenever a dimension attribute value is written for an
     * {@link EntityRole#EXTENSION} instance, re-reads *all* of that entity
     * definition's dimension attribute values currently written for this
     * instance (in {@code position} order) — if every one of them now has
     * a value, assembles and syncs the composite {@code businessKey}
     * ({@code "<anchor businessKey>|<dim1>|<dim2>..."}). If {@code
     * attribute} isn't one of the dimension attributes, or not every
     * dimension attribute has a value yet, this is a no-op (the composite
     * key is only ever written once *complete* — a partially-written
     * instance simply has no {@code businessKey} yet, same generic
     * "0 dòng → null" spirit as D-019's single-attribute key).
     */
    private void recomputeExtensionBusinessKeyIfComplete(
            EntityInstance instance, EntityDefinition entityDefinition, AttributeDefinition attribute) {
        List<EntityDefinitionDimensionAttribute> dimensions =
                dimensionAttributeRepository.findByEntityDefinitionOrderByPositionAsc(entityDefinition);
        boolean attributeIsADimension =
                dimensions.stream()
                        .anyMatch(dimension -> isSameAttribute(attribute, dimension.getAttributeDefinition()));
        if (!attributeIsADimension) {
            return;
        }

        EntityInstance anchorInstance = instance.getAnchorInstance();
        if (anchorInstance == null) {
            return;
        }

        StringBuilder businessKey = new StringBuilder(anchorInstance.getBusinessKey());
        for (EntityDefinitionDimensionAttribute dimension : dimensions) {
            Optional<EntityInstanceAttributeValue> dimensionValue =
                    attributeValueRepository.findByEntityInstanceAndAttributeDefinition(
                            instance, dimension.getAttributeDefinition());
            if (dimensionValue.isEmpty()) {
                return; // not all dimension values written yet — leave businessKey as-is
            }
            businessKey.append(BUSINESS_KEY_SEPARATOR).append(dimensionValue.get().asDisplayString());
        }

        instance.syncBusinessKey(businessKey.toString());
        entityInstanceRepository.save(instance);
    }

    private static boolean isSameAttribute(AttributeDefinition a, AttributeDefinition b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }
}
