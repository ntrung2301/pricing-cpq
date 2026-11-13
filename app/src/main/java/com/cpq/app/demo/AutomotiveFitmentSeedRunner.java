package com.cpq.app.demo;

import com.cpq.app.constraint.ConstraintDefinitionRepository;
import com.cpq.app.constraint.ConstraintInstanceRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.app.metamodel.RelationshipDefinitionRepository;
import com.cpq.app.metamodel.RelationshipInstanceRepository;
import com.cpq.app.pack.PackLoaderService;
import com.cpq.core.api.catalog.CatalogImportService;
import com.cpq.core.api.catalog.ImportResultDto;
import com.cpq.core.metamodel.ConstraintDefinition;
import com.cpq.core.metamodel.ConstraintInstance;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.RelationshipDefinition;
import com.cpq.core.metamodel.RelationshipInstance;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Seeds real automotive fitment + supersession demo data (S2-T7,
 * sprint-2.md), on top of the Product/Customer data {@link
 * DemoDataSeedRunner} (S2-T6) already seeds:
 *
 * <ol>
 *   <li>Imports 5 {@code Vehicle} entity_instances from {@code
 *       classpath:demo-data/automotive/vehicles.csv} via the existing
 *       {@link CatalogImportService} (S2-T5/D-022) — no new import logic.
 *   <li>Creates {@code constraint_instance} rows of type {@code
 *       "compatibility"} (S2-T2) pairing some of those Vehicles with some
 *       already-seeded Products — this is exactly the "fitment" use of
 *       the generic compatibility mechanism ADR-003 describes, so {@code
 *       GET /api/configurator/compatible} (S2-T8, unmodified) answers
 *       "which parts fit vehicle X" correctly once this data exists.
 *   <li>Creates {@code relationship_instance} rows of relationship_def
 *       code {@code "supersedes"} (S2-T3/D-025) for the 5 superseded/
 *       replacement SKU pairs {@code products.csv} (S2-T6/D-026) already
 *       named by example.
 * </ol>
 *
 * <p><b>Why a separate runner from {@link DemoDataSeedRunner}:</b> S2-T6's
 * scope was CSV entity_instance import only; this task's data (constraint_
 * instance/relationship_instance rows referencing specific instances) is a
 * different shape of seeding that didn't exist when S2-T6 was written, and
 * keeping it in its own class keeps each runner's seeding concern (and
 * Definition of Done) separately testable — same reasoning that already
 * split {@code PackLoaderService} from {@code DemoDataSeedRunner}. See
 * docs/decisions.md D-029.
 *
 * <p><b>Ordering:</b> same strategy D-026 chose for {@code
 * DemoDataSeedRunner} vs. {@code PackLoaderService} — rather than rely on
 * Spring Boot's unspecified invocation order between {@code
 * ApplicationRunner} beans, this runner calls {@code
 * DemoDataSeedRunner#run} and {@code PackLoaderService
 * #loadAllPacksFromClasspath} itself first. Both are already idempotent,
 * so calling them again when Spring also invokes them as their own
 * runners is a harmless no-op, and it guarantees Product/Customer
 * instances and the Vehicle entity_def exist before this runner needs
 * them, regardless of what order Spring actually chose.
 *
 * <p><b>Fitment/supersession pairing data lives in Java, not a CSV or
 * pack.yaml:</b> unlike entity_instance rows, there is no import
 * mechanism yet for constraint_instance/relationship_instance rows (only
 * S2-T5's entity_instance CSV importer exists) — so which Vehicle pairs
 * with which Product SKUs, and which SKU supersedes which, is a fixed
 * lookup table below. NEEDS-PO-REVIEW (noted in D-029): if a future task
 * adds a generic constraint_instance/relationship_instance import/seed
 * mechanism, this hardcoded plan should move to data too.
 *
 * <p><b>Fitment condition is a literal {@code true}</b> ({@code
 * ALWAYS_TRUE_CONDITION}), not a comparison between Product/Vehicle
 * attributes: Product and Vehicle share no common attribute to compare
 * (ADR-002's condition grammar needs a {@code var} on each side), so for
 * this curated seed data, <em>which</em> (Product, Vehicle) pairs get a
 * constraint_instance row encodes the actual fitment decision — same
 * framing D-024 point 2 already gave constraint_instance rows in general
 * ("one concrete assertion between two specific instances").
 *
 * <p><b>Idempotency on restart:</b> each of the 3 steps above checks
 * first — if the Vehicle entity_def already has instances, or the fixed
 * {@code "automotive-fitment"} constraint_def already exists, or any
 * {@code "supersedes"} relationship_instance already exists — that step
 * is skipped entirely, same "any row present = fully seeded" trade-off
 * {@code DemoDataSeedRunner} (D-026 point 4) already accepted.
 *
 * <p><b>RBAC:</b> same synthetic {@code ROLE_PRICING_ADMIN} authentication
 * pattern {@code DemoDataSeedRunner} uses to call {@code
 * CatalogImportService#importCsv} from a non-HTTP {@code
 * ApplicationRunner} context (D-026 point 5).
 */
@Component
public class AutomotiveFitmentSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AutomotiveFitmentSeedRunner.class);

    static final String VEHICLE_ENTITY_DEF_CODE = "Vehicle";
    private static final String VEHICLES_CSV_CLASSPATH_LOCATION = "demo-data/automotive/vehicles.csv";

    static final String FITMENT_CONSTRAINT_DEF_CODE = "automotive-fitment";
    private static final String COMPATIBILITY_CONSTRAINT_TYPE = "compatibility";
    private static final String ALWAYS_TRUE_CONDITION = "{\"==\": [1, 1]}";

    static final String SUPERSEDES_RELATIONSHIP_CODE = "supersedes";

    /** Vehicle CSV row index (0-based, {@code vehicles.csv} row order) -> compatible Product SKUs. */
    private static final Map<Integer, List<String>> FITMENT_PLAN =
            Map.of(
                    0, List.of("BRK-PAD-NEW-001", "BRK-ROTOR-NEW-001", "OIL-FLT-NEW-010", "AIR-FLT-001"),
                    1, List.of("BRK-PAD-NEW-002", "SPK-PLUG-001", "WIP-BLD-001"),
                    2, List.of("ALT-NEW-500", "BAT-002", "SHK-ABS-001"),
                    3, List.of("BRK-PAD-OLD-001", "ALT-OLD-500", "BRK-ROTOR-OLD-001"),
                    4, List.of("OIL-FLT-OLD-010", "AIR-FLT-002", "BRK-PAD-OLD-002"));

    /** Replacement SKU -> superseded SKU (D-025 point 3: "from" = replacement, "to" = superseded). */
    private static final Map<String, String> SUPERSESSION_PLAN =
            Map.of(
                    "BRK-PAD-NEW-001", "BRK-PAD-OLD-001",
                    "BRK-PAD-NEW-002", "BRK-PAD-OLD-002",
                    "BRK-ROTOR-NEW-001", "BRK-ROTOR-OLD-001",
                    "OIL-FLT-NEW-010", "OIL-FLT-OLD-010",
                    "ALT-NEW-500", "ALT-OLD-500");

    private final DemoDataSeedRunner demoDataSeedRunner;
    private final PackLoaderService packLoaderService;
    private final CatalogImportService catalogImportService;
    private final EntityInstanceRepository entityInstanceRepository;
    private final ConstraintDefinitionRepository constraintDefinitionRepository;
    private final ConstraintInstanceRepository constraintInstanceRepository;
    private final RelationshipDefinitionRepository relationshipDefinitionRepository;
    private final RelationshipInstanceRepository relationshipInstanceRepository;

    public AutomotiveFitmentSeedRunner(
            DemoDataSeedRunner demoDataSeedRunner,
            PackLoaderService packLoaderService,
            CatalogImportService catalogImportService,
            EntityInstanceRepository entityInstanceRepository,
            ConstraintDefinitionRepository constraintDefinitionRepository,
            ConstraintInstanceRepository constraintInstanceRepository,
            RelationshipDefinitionRepository relationshipDefinitionRepository,
            RelationshipInstanceRepository relationshipInstanceRepository) {
        this.demoDataSeedRunner = demoDataSeedRunner;
        this.packLoaderService = packLoaderService;
        this.catalogImportService = catalogImportService;
        this.entityInstanceRepository = entityInstanceRepository;
        this.constraintDefinitionRepository = constraintDefinitionRepository;
        this.constraintInstanceRepository = constraintInstanceRepository;
        this.relationshipDefinitionRepository = relationshipDefinitionRepository;
        this.relationshipInstanceRepository = relationshipInstanceRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        demoDataSeedRunner.run(args);
        packLoaderService.loadAllPacksFromClasspath();

        List<EntityInstance> vehicles = seedVehiclesIfAbsent();
        seedFitmentIfAbsent(vehicles);
        seedSupersessionIfAbsent();
    }

    private List<EntityInstance> seedVehiclesIfAbsent() throws IOException {
        List<EntityInstance> existing =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc(VEHICLE_ENTITY_DEF_CODE);
        if (!existing.isEmpty()) {
            log.info("Demo Vehicle instances already present — skipping seed (idempotent restart, S2-T7)");
            return existing;
        }

        String csvContent = readClasspathCsv(VEHICLES_CSV_CLASSPATH_LOCATION);
        ImportResultDto result =
                asPricingAdmin(
                        () ->
                                catalogImportService.importCsv(
                                        VEHICLE_ENTITY_DEF_CODE, VEHICLES_CSV_CLASSPATH_LOCATION, csvContent));
        log.info(
                "Seeded demo Vehicle instances: status={}, created={}, rowErrors={}",
                result.status(),
                result.createdCount(),
                result.errors().size());
        return entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc(VEHICLE_ENTITY_DEF_CODE);
    }

    private void seedFitmentIfAbsent(List<EntityInstance> vehicles) {
        if (constraintDefinitionRepository.findByCode(FITMENT_CONSTRAINT_DEF_CODE).isPresent()) {
            log.info("Automotive fitment constraint_instance rows already present — skipping seed (S2-T7)");
            return;
        }

        ConstraintDefinition fitmentDefinition =
                constraintDefinitionRepository.save(
                        new ConstraintDefinition(FITMENT_CONSTRAINT_DEF_CODE, COMPATIBILITY_CONSTRAINT_TYPE));

        int created = 0;
        for (Map.Entry<Integer, List<String>> entry : FITMENT_PLAN.entrySet()) {
            int vehicleIndex = entry.getKey();
            if (vehicleIndex >= vehicles.size()) {
                log.warn("Skipping fitment seed — no Vehicle at row index {}", vehicleIndex);
                continue;
            }
            EntityInstance vehicle = vehicles.get(vehicleIndex);
            for (String sku : entry.getValue()) {
                EntityInstance product = findProductByBusinessKey(sku);
                if (product == null) {
                    log.warn("Skipping fitment seed — unknown Product SKU '{}'", sku);
                    continue;
                }
                constraintInstanceRepository.save(
                        new ConstraintInstance(fitmentDefinition, ALWAYS_TRUE_CONDITION, product, vehicle));
                created++;
            }
        }
        log.info("Seeded {} automotive fitment constraint_instance row(s)", created);
    }

    private void seedSupersessionIfAbsent() {
        if (relationshipInstanceRepository.existsByRelationshipDefinition_Code(SUPERSEDES_RELATIONSHIP_CODE)) {
            log.info(
                    "Automotive supersession relationship_instance rows already present — skipping seed"
                            + " (S2-T7)");
            return;
        }

        RelationshipDefinition supersedes =
                relationshipDefinitionRepository
                        .findByCode(SUPERSEDES_RELATIONSHIP_CODE)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "relationship_def '"
                                                        + SUPERSEDES_RELATIONSHIP_CODE
                                                        + "' not found — pack.yaml should have defined it"
                                                        + " already (S2-T3/D-025)"));

        int created = 0;
        for (Map.Entry<String, String> entry : SUPERSESSION_PLAN.entrySet()) {
            EntityInstance replacement = findProductByBusinessKey(entry.getKey());
            EntityInstance superseded = findProductByBusinessKey(entry.getValue());
            if (replacement == null || superseded == null) {
                log.warn(
                        "Skipping supersession seed — unknown Product SKU pair '{}' -> '{}'",
                        entry.getKey(),
                        entry.getValue());
                continue;
            }
            relationshipInstanceRepository.save(new RelationshipInstance(supersedes, replacement, superseded, null));
            created++;
        }
        log.info("Seeded {} automotive supersession relationship_instance row(s)", created);
    }

    private EntityInstance findProductByBusinessKey(String sku) {
        return entityInstanceRepository
                .findByEntityDefinition_CodeAndBusinessKey(DemoDataSeedRunner.PRODUCT_ENTITY_DEF_CODE, sku)
                .orElse(null);
    }

    private static String readClasspathCsv(String classpathLocation) throws IOException {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    private <T> T asPricingAdmin(Supplier<T> action) {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        Authentication seedRunnerAuthentication =
                new UsernamePasswordAuthenticationToken(
                        "fitment-seed-runner", null, List.of(new SimpleGrantedAuthority("ROLE_PRICING_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(seedRunnerAuthentication);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        }
    }
}
