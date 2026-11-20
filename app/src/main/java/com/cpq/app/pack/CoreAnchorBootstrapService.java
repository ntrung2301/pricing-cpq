package com.cpq.app.pack;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityRole;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the 2 fixed core "anchor" entity definitions — {@code Product}
 * and {@code Customer} — at application startup if they don't already
 * exist (ADR-009 mục 1, D-037/D-038/D-039, E8-01/S3-T1): "core biết có
 * đúng 2 anchor này" — unlike every other entity definition, these 2 are
 * never *created* by a domain pack's {@code pack.yaml}; a pack only ever
 * adds attributes/designates key/label onto the shell this service
 * creates (upsert-by-code, via {@code PackLoaderService}'s existing D-019/
 * D-020 mechanism — unchanged).
 *
 * <p><b>Ordering vs. {@link PackLoaderService}:</b> both are {@link
 * ApplicationRunner} beans; this one carries {@code @Order(1)},
 * {@code PackLoaderService} carries {@code @Order(2)} — Spring Boot
 * invokes {@code ApplicationRunner}s in ascending {@code @Order} value, so
 * this always runs first. A pack that upserts attributes onto {@code
 * Product}/{@code Customer} by code needs those {@code entity_def} rows
 * (with {@code role = ANCHOR} already set) to exist first. Other {@code
 * ApplicationRunner} beans in {@code app} ({@code DemoDataSeedRunner},
 * {@code AutomotiveFitmentSeedRunner}, {@code AutomotivePricingSeedRunner})
 * carry no {@code @Order} and are therefore still invoked after both of
 * these (Spring treats unannotated runners as {@code Ordered.LOWEST_PRECEDENCE}
 * for comparison purposes) — no behavioural change for them.
 *
 * <p><b>Why both this runner and migration {@code
 * V9__entity_taxonomy_v2.sql} backfill {@code role = 'ANCHOR'}:</b> the
 * migration runs exactly once, against whatever {@code entity_def} rows a
 * deployment that already ran Sprint 2's automotive pack happens to have
 * (D-039). This runner is the mechanism that keeps a *new* deployment
 * (empty database, no pack ever run) correct from its very first startup,
 * and is deliberately idempotent/defensive — re-asserting {@code role =
 * ANCHOR} on every startup is a harmless no-op once it's already correct.
 */
@Component
@Order(1)
public class CoreAnchorBootstrapService implements ApplicationRunner {

    static final String PRODUCT_CODE = "Product";
    static final String CUSTOMER_CODE = "Customer";

    private static final List<String> CORE_ANCHOR_CODES = List.of(PRODUCT_CODE, CUSTOMER_CODE);

    private final EntityDefinitionRepository entityDefinitionRepository;

    public CoreAnchorBootstrapService(EntityDefinitionRepository entityDefinitionRepository) {
        this.entityDefinitionRepository = entityDefinitionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapCoreAnchors();
    }

    /**
     * For each of {@code Product}/{@code Customer}: creates a 0-attribute
     * shell ({@code label = code}, {@code version = 1}) if no entity_def
     * with that code exists yet, then (either way) ensures {@code role ==
     * EntityRole.ANCHOR}. Never touches attributes/key/label designations
     * of an already-existing row — that remains entirely
     * {@code PackLoaderService}'s job.
     */
    @Transactional
    public void bootstrapCoreAnchors() {
        for (String code : CORE_ANCHOR_CODES) {
            EntityDefinition entityDefinition =
                    entityDefinitionRepository
                            .findByCodeWithAttributes(code)
                            .orElseGet(() -> new EntityDefinition(code, code, 1));
            entityDefinition.assignRole(EntityRole.ANCHOR);
            entityDefinitionRepository.save(entityDefinition);
        }
    }
}
