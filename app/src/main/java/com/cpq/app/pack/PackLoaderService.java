package com.cpq.app.pack;

import com.cpq.app.metamodel.EntityDefinitionDimensionAttributeRepository;
import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.RelationshipDefinitionRepository;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityDefinitionDimensionAttribute;
import com.cpq.core.metamodel.EntityRole;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
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
 *
 * <p><b>Entity taxonomy v2 (ADR-009 mục 1, D-037/D-038/D-039, S3-T1):</b>
 * {@code role}/{@code extends}/{@code dimension_attributes} on {@code
 * EntityDescriptor} are all optional. If a pack doesn't declare {@code
 * role} for an entity, this service leaves that entity_def's {@code role}
 * column exactly as it already is (D-039 — "giữ nguyên hành vi cũ"),
 * whatever that was (null for a brand-new entity, or whatever a previous
 * load/{@code CoreAnchorBootstrapService} already set). A pack is never
 * allowed to set {@code role} for entity code {@code Product}/{@code
 * Customer} — that is exclusively {@code CoreAnchorBootstrapService}'s
 * job, running with {@code @Order(1)} strictly before this service's
 * {@code @Order(2)}; if a pack declares one anyway, it is logged as a
 * warning and silently ignored (not a hard failure — existing packs that
 * happen to declare it must not be blocked by this). For any other
 * entity, a declared {@code role == EXTENSION} requires {@code extends}
 * to name an already-upserted-in-this-pack or already-persisted entity_def
 * (clear {@link PackLoadException} otherwise), and each {@code
 * dimension_attributes} code must match one of the entity's own declared
 * attributes (clear {@link PackLoadException} otherwise) — recorded as
 * ordered {@link EntityDefinitionDimensionAttribute} rows. Unlike plain
 * attribute upsert, dimension attributes are fully replaced (delete all,
 * re-insert) on every reload of the same entity — there is no accretive
 * state to preserve there, the ordered list *is* the pack's current
 * declaration.
 */
@Service
@Order(2)
public class PackLoaderService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PackLoaderService.class);

    static final int MAX_ATTRIBUTES_PER_ENTITY = 20;

    private static final String CLASSPATH_PACK_PATTERN = "classpath*:config/packs/*/pack.yaml";

    private final EntityDefinitionRepository entityDefinitionRepository;
    private final RelationshipDefinitionRepository relationshipDefinitionRepository;
    private final EntityDefinitionDimensionAttributeRepository dimensionAttributeRepository;
    private final ResourcePatternResolver resourcePatternResolver;
    private final ObjectMapper yamlMapper;

    public PackLoaderService(
            EntityDefinitionRepository entityDefinitionRepository,
            RelationshipDefinitionRepository relationshipDefinitionRepository,
            EntityDefinitionDimensionAttributeRepository dimensionAttributeRepository,
            ResourcePatternResolver resourcePatternResolver) {
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.relationshipDefinitionRepository = relationshipDefinitionRepository;
        this.dimensionAttributeRepository = dimensionAttributeRepository;
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
        // Second pass (ADR-009 mục 1/D-039): role/extends/dimension_attributes,
        // processed after every entity in this pack has been upserted so
        // 'extends' can reference any entity declared anywhere in the same
        // pack (order-independent), not just ones declared earlier.
        for (EntityDescriptor entityDescriptor : pack.entities()) {
            applyRoleAndTaxonomy(pack, entityDescriptor, byCode);
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

    /**
     * Second pass of entity taxonomy v2 (ADR-009 mục 1, D-039) — see this
     * class's javadoc for the full set of rules. No-op if {@code
     * entityDescriptor.role()} is null (pack didn't declare a role).
     */
    private void applyRoleAndTaxonomy(
            PackDescriptor pack, EntityDescriptor entityDescriptor, Map<String, EntityDefinition> byCode) {
        if (entityDescriptor.role() == null) {
            return;
        }

        if (CoreAnchorBootstrapService.PRODUCT_CODE.equals(entityDescriptor.code())
                || CoreAnchorBootstrapService.CUSTOMER_CODE.equals(entityDescriptor.code())) {
            log.warn(
                    "Pack '{}' declares role '{}' for entity '{}', which is a fixed core anchor managed "
                            + "exclusively by CoreAnchorBootstrapService (ADR-009 mục 1) — ignoring the "
                            + "pack-declared role, not failing the load (D-039).",
                    pack.packId(),
                    entityDescriptor.role(),
                    entityDescriptor.code());
            return;
        }

        EntityDefinition entityDefinition = byCode.get(entityDescriptor.code());
        EntityRole role = parseRole(pack, entityDescriptor);
        entityDefinition.assignRole(role);

        if (role == EntityRole.EXTENSION) {
            applyExtension(pack, entityDescriptor, entityDefinition, byCode);
        }

        entityDefinitionRepository.save(entityDefinition);
    }

    private EntityRole parseRole(PackDescriptor pack, EntityDescriptor entityDescriptor) {
        try {
            return EntityRole.valueOf(entityDescriptor.role());
        } catch (IllegalArgumentException e) {
            throw new PackLoadException(
                    "Pack '"
                            + pack.packId()
                            + "' rejected: entity '"
                            + entityDescriptor.code()
                            + "' declares unknown role '"
                            + entityDescriptor.role()
                            + "' (expected one of ANCHOR, EXTENSION, PARAMETER)");
        }
    }

    private void applyExtension(
            PackDescriptor pack,
            EntityDescriptor entityDescriptor,
            EntityDefinition entityDefinition,
            Map<String, EntityDefinition> byCode) {
        String extendsCode = entityDescriptor.extendsEntityCode();
        if (extendsCode == null) {
            throw new PackLoadException(
                    "Pack '"
                            + pack.packId()
                            + "' rejected: entity '"
                            + entityDescriptor.code()
                            + "' has role EXTENSION but does not declare 'extends'");
        }
        EntityDefinition extended =
                resolveEntity(
                        pack, extendsCode, byCode, "entity '" + entityDescriptor.code() + "' (extends)");
        entityDefinition.assignExtendsEntityDefinition(extended);

        Map<String, AttributeDefinition> ownAttributesByCode = new HashMap<>();
        for (AttributeDefinition attribute : entityDefinition.getAttributes()) {
            ownAttributesByCode.put(attribute.getCode(), attribute);
        }

        // Full replace (not additive) — see class javadoc: the ordered
        // dimension list *is* the pack's current declaration, nothing to
        // accrete across reloads.
        //
        // flush() right after the delete (S3-T4 regression fix, found while
        // adding automotive's first real EXTENSION entity, CostHistory):
        // without it, the delete and the re-insert below share one flush at
        // transaction commit, and Hibernate's action queue always executes
        // entity insertions before entity deletions regardless of the order
        // they were queued in Java — so re-inserting the same (entity_def_id,
        // attribute_def_id) pair ran *before* the old row's delete, tripping
        // uq_edda_entity_def_attribute. Every ApplicationRunner at real app
        // startup calls loadAllPacksFromClasspath itself first (idempotency
        // pattern), so a pack with an EXTENSION entity really does get
        // reloaded several times across separate transactions in one run —
        // this was unreachable before CostHistory because no real pack had
        // declared an EXTENSION entity yet.
        List<EntityDefinitionDimensionAttribute> existing =
                dimensionAttributeRepository.findByEntityDefinitionOrderByPositionAsc(entityDefinition);
        if (!existing.isEmpty()) {
            dimensionAttributeRepository.deleteAll(existing);
            dimensionAttributeRepository.flush();
        }

        int position = 0;
        for (String dimensionAttributeCode : entityDescriptor.dimensionAttributes()) {
            AttributeDefinition dimensionAttribute = ownAttributesByCode.get(dimensionAttributeCode);
            if (dimensionAttribute == null) {
                throw new PackLoadException(
                        "Pack '"
                                + pack.packId()
                                + "' rejected: entity '"
                                + entityDescriptor.code()
                                + "' declares dimension attribute '"
                                + dimensionAttributeCode
                                + "' which is not one of its declared attributes");
            }
            dimensionAttributeRepository.save(
                    new EntityDefinitionDimensionAttribute(entityDefinition, dimensionAttribute, position));
            position++;
        }
    }

    private void upsertRelationship(
            PackDescriptor pack, RelationshipDescriptor relationshipDescriptor, Map<String, EntityDefinition> byCode) {
        String relationshipDescription = "relationship '" + relationshipDescriptor.code() + "'";
        EntityDefinition from =
                resolveEntity(pack, relationshipDescriptor.fromEntity(), byCode, relationshipDescription);
        EntityDefinition to = resolveEntity(pack, relationshipDescriptor.toEntity(), byCode, relationshipDescription);

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

    /**
     * Resolves {@code entityCode} to an entity definition — one already
     * upserted earlier in this same pack load ({@code byCode}), or one
     * already persisted from a previous load (this pack or another).
     * {@code referenceDescription} (e.g. {@code "relationship 'supersedes'"},
     * {@code "entity 'CostHistory' (extends)"}) names *what* is making the
     * reference, so the thrown {@link PackLoadException} (if {@code
     * entityCode} resolves to nothing) is specific enough to act on.
     */
    private EntityDefinition resolveEntity(
            PackDescriptor pack, String entityCode, Map<String, EntityDefinition> byCode, String referenceDescription) {
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
                                                + "' rejected: "
                                                + referenceDescription
                                                + " references unknown entity '"
                                                + entityCode
                                                + "'"));
    }
}
