package com.cpq.app.pack;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.RelationshipDefinitionRepository;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.RelationshipDefinition;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads domain pack(s) from {@code pack.yaml} (ADR-001) and upserts their
 * {@code entities[]}/{@code relationships[]} into the 4 generic metamodel
 * *definition* tables, tagging every row with {@code pack_id}/{@code
 * pack_version}. Lives in {@code app} (not {@code core-metamodel}), per
 * ADR-001: "{@code PackLoaderService} thuộc app, chỉ biết cấu trúc generic
 * của {@code pack.yaml}" — it has no idea what any domain pack's entity
 * codes mean.
 *
 * <p>Runs automatically at application startup ({@link ApplicationRunner}),
 * scanning {@code classpath*:config/packs/*&#47;pack.yaml}. At S2-T1 no
 * real domain pack exists yet (automotive lands in S2-T3/S2-T4) — zero
 * matches is the normal/expected case and is a safe no-op.
 *
 * <p>Validation (D-019): an entity declaring more than
 * {@value #MAX_ATTRIBUTES_PER_ENTITY} attributes rejects the *whole* pack
 * (nothing from it is persisted — loading one pack runs in one
 * transaction). {@code key_attribute}/{@code label_attribute} must name
 * one of the entity's own declared attribute codes.
 *
 * <p>Upsert semantics are intentionally simple/additive for S2-T1: an
 * existing entity_def (same code) gets its label/pack metadata refreshed
 * and any attribute codes not yet present get added; nothing is ever
 * deleted or type-changed. The fuller "diff + reject breaking changes
 * across a pack version bump" mechanism ADR-001 mentions is explicitly
 * deferred (flagged NEEDS-PO-REVIEW, see docs/decisions.md D-020) — not
 * required by this task's Definition of Done.
 */
@Service
public class PackLoaderService implements ApplicationRunner {

    static final int MAX_ATTRIBUTES_PER_ENTITY = 20;

    private static final String CLASSPATH_PACK_PATTERN = "classpath*:config/packs/*/pack.yaml";

    private final EntityDefinitionRepository entityDefinitionRepository;
    private final RelationshipDefinitionRepository relationshipDefinitionRepository;
    private final ResourcePatternResolver resourcePatternResolver;
    private final ObjectMapper yamlMapper;

    public PackLoaderService(
            EntityDefinitionRepository entityDefinitionRepository,
            RelationshipDefinitionRepository relationshipDefinitionRepository,
            ResourcePatternResolver resourcePatternResolver) {
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.relationshipDefinitionRepository = relationshipDefinitionRepository;
        this.resourcePatternResolver = resourcePatternResolver;
        this.yamlMapper =
                YAMLMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        loadAllPacksFromClasspath();
    }

    /**
     * Scans {@value #CLASSPATH_PACK_PATTERN} and loads every matching
     * {@code pack.yaml} found (0 matches is a valid, common result).
     */
    public List<EntityDefinition> loadAllPacksFromClasspath() throws IOException {
        Resource[] resources = resourcePatternResolver.getResources(CLASSPATH_PACK_PATTERN);
        List<EntityDefinition> loaded = new java.util.ArrayList<>();
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                loaded.addAll(loadPack(in));
            }
        }
        return loaded;
    }

    /** Parses and loads one {@code pack.yaml}'s raw YAML content. */
    public List<EntityDefinition> loadPack(String yamlContent) {
        return loadPack(parse(yamlContent));
    }

    private List<EntityDefinition> loadPack(InputStream yamlContent) {
        try {
            return loadPack(yamlMapper.readValue(yamlContent, PackDescriptor.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse pack.yaml", e);
        }
    }

    private PackDescriptor parse(String yamlContent) {
        try {
            return yamlMapper.readValue(yamlContent, PackDescriptor.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse pack.yaml", e);
        }
    }

    @Transactional
    public List<EntityDefinition> loadPack(PackDescriptor pack) {
        validateAttributeCap(pack);

        Map<String, EntityDefinition> byCode = new LinkedHashMap<>();
        for (EntityDescriptor entityDescriptor : pack.entities()) {
            EntityDefinition saved = upsertEntity(pack, entityDescriptor);
            byCode.put(saved.getCode(), saved);
        }
        for (RelationshipDescriptor relationshipDescriptor : pack.relationships()) {
            upsertRelationship(pack, relationshipDescriptor, byCode);
        }
        return List.copyOf(byCode.values());
    }

    private void validateAttributeCap(PackDescriptor pack) {
        for (EntityDescriptor entityDescriptor : pack.entities()) {
            int count = entityDescriptor.attributes().size();
            if (count > MAX_ATTRIBUTES_PER_ENTITY) {
                throw new PackLoadException(
                        "Pack '"
                                + pack.packId()
                                + "' rejected: entity '"
                                + entityDescriptor.code()
                                + "' declares "
                                + count
                                + " attributes, exceeding the max of "
                                + MAX_ATTRIBUTES_PER_ENTITY
                                + " per entity_def (D-019)");
            }
        }
    }

    private EntityDefinition upsertEntity(PackDescriptor pack, EntityDescriptor entityDescriptor) {
        EntityDefinition entityDefinition =
                entityDefinitionRepository
                        .findByCodeWithAttributes(entityDescriptor.code())
                        .orElseGet(() -> new EntityDefinition(entityDescriptor.code(), entityDescriptor.label(), 1));
        entityDefinition.assignPack(pack.packId(), pack.version());

        Map<String, AttributeDefinition> attributesByCode = new HashMap<>();
        for (AttributeDefinition existing : entityDefinition.getAttributes()) {
            attributesByCode.put(existing.getCode(), existing);
        }
        for (AttributeDescriptor attributeDescriptor : entityDescriptor.attributes()) {
            AttributeDefinition attribute = attributesByCode.get(attributeDescriptor.code());
            if (attribute == null) {
                attribute =
                        new AttributeDefinition(
                                attributeDescriptor.code(),
                                attributeDescriptor.label(),
                                attributeDescriptor.dataType(),
                                attributeDescriptor.required(),
                                attributeDescriptor.externalCodeHint());
                entityDefinition.addAttribute(attribute);
                attributesByCode.put(attributeDescriptor.code(), attribute);
            }
            attribute.assignPack(pack.packId(), pack.version());
        }

        EntityDefinition saved = entityDefinitionRepository.save(entityDefinition);
        // Re-read the (now id-assigned) attribute map off the saved aggregate so
        // designateKeyAttribute/designateLabelAttribute's ownership check passes.
        Map<String, AttributeDefinition> savedAttributesByCode = new HashMap<>();
        for (AttributeDefinition attribute : saved.getAttributes()) {
            savedAttributesByCode.put(attribute.getCode(), attribute);
        }

        if (entityDescriptor.keyAttribute() != null) {
            saved.designateKeyAttribute(
                    requireDeclaredAttribute(pack, entityDescriptor, savedAttributesByCode, entityDescriptor.keyAttribute()));
        }
        if (entityDescriptor.labelAttribute() != null) {
            saved.designateLabelAttribute(
                    requireDeclaredAttribute(
                            pack, entityDescriptor, savedAttributesByCode, entityDescriptor.labelAttribute()));
        }

        return entityDefinitionRepository.save(saved);
    }

    private AttributeDefinition requireDeclaredAttribute(
            PackDescriptor pack,
            EntityDescriptor entityDescriptor,
            Map<String, AttributeDefinition> attributesByCode,
            String attributeCode) {
        AttributeDefinition attribute = attributesByCode.get(attributeCode);
        if (attribute == null) {
            throw new PackLoadException(
                    "Pack '"
                            + pack.packId()
                            + "' rejected: entity '"
                            + entityDescriptor.code()
                            + "' designates key/label attribute '"
                            + attributeCode
                            + "' which is not one of its declared attributes");
        }
        return attribute;
    }

    private void upsertRelationship(
            PackDescriptor pack, RelationshipDescriptor relationshipDescriptor, Map<String, EntityDefinition> byCode) {
        EntityDefinition from = resolveEntity(pack, relationshipDescriptor.fromEntity(), byCode);
        EntityDefinition to = resolveEntity(pack, relationshipDescriptor.toEntity(), byCode);

        RelationshipDefinition relationship =
                relationshipDefinitionRepository
                        .findByCode(relationshipDescriptor.code())
                        .orElseGet(
                                () ->
                                        new RelationshipDefinition(
                                                relationshipDescriptor.code(), from, to, relationshipDescriptor.cardinality()));
        relationship.assignPack(pack.packId(), pack.version());
        relationshipDefinitionRepository.save(relationship);
    }

    private EntityDefinition resolveEntity(
            PackDescriptor pack, String entityCode, Map<String, EntityDefinition> byCode) {
        EntityDefinition inThisPack = byCode.get(entityCode);
        if (inThisPack != null) {
            return inThisPack;
        }
        return entityDefinitionRepository
                .findByCodeWithAttributes(entityCode)
                .orElseThrow(
                        () ->
                                new PackLoadException(
                                        "Pack '"
                                                + pack.packId()
                                                + "' rejected: relationship references unknown entity '"
                                                + entityCode
                                                + "'"));
    }
}
