package com.cpq.app.demo;

import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.app.pack.PackLoaderService;
import com.cpq.core.api.catalog.CatalogImportService;
import com.cpq.core.api.catalog.ImportResultDto;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * Seeds real automotive demo data (S2-T6, sprint-2.md) at application
 * startup: imports {@code Product} and {@code Customer} entity instances
 * from two CSV files shipped under {@code classpath:demo-data/automotive/}
 * via S2-T5's existing {@link CatalogImportService} (D-022) — this class
 * does *not* re-implement any import/parsing logic, it only calls the
 * existing port, same pattern {@code ImportController} itself uses.
 *
 * <p>Replaces the ad-hoc {@code SampleWidget} seed (D-018 mục 3, migration
 * {@code V3__seed_sample_entity_definition.sql}) as the demo data used by
 * later tasks (S2-T7/S2-T9/S2-T12) — {@code SampleWidget} itself is left in
 * place (dropping/editing an already-applied Flyway migration is treated as
 * a separate decision, flagged NEEDS-PO-REVIEW, see docs/decisions.md).
 *
 * <p><b>Ordering vs. {@link PackLoaderService}:</b> both this class and
 * {@code PackLoaderService} are {@link ApplicationRunner} beans; Spring
 * Boot does not guarantee invocation order between two runners that
 * neither implements {@code Ordered} nor carries {@code @Order} (and
 * {@code PackLoaderService} intentionally isn't touched by this task).
 * Rather than rely on an unspecified ordering, this runner calls
 * {@link PackLoaderService#loadAllPacksFromClasspath()} itself first —
 * that upsert is already idempotent (S2-T1), so calling it a second time
 * when Spring also invokes {@code PackLoaderService} as its own runner is
 * a harmless no-op, and it guarantees the {@code Product}/{@code Customer}
 * {@code entity_def} rows this runner imports against always exist first,
 * regardless of what order Spring actually chose.
 *
 * <p><b>Idempotency on restart:</b> rather than importing unconditionally
 * and relying on the {@code business_key} unique constraint (S2-T1) to
 * reject duplicate rows one by one (which is how S2-T5's importer already
 * behaves and would still not crash — see
 * {@code CatalogImportServiceImpl}'s per-row {@code DataAccessException}
 * handling), this runner checks first: if *any* {@code entity_instance}
 * already exists for {@code Product}/{@code Customer}, the whole CSV for
 * that entity is skipped. This avoids generating a noisy all-duplicate
 * {@code import_job} on every single app restart. Trade-off (documented,
 * NEEDS-PO-REVIEW candidate): a prior run that crashed halfway through
 * will not be "topped up" on the next restart, since presence of any row
 * is treated as "fully seeded" — acceptable for demo-seed scope.
 *
 * <p><b>RBAC:</b> {@link CatalogImportService#importCsv} is
 * {@code @PreAuthorize("hasRole('PRICING_ADMIN')")}-guarded (ADR-006) and
 * enforced at the method layer via {@code @EnableMethodSecurity}, which
 * applies to *any* caller, including this in-process startup call — there
 * is no HTTP request/JWT at that point, so no ambient
 * {@code SecurityContext} exists yet. A synthetic
 * {@code ROLE_PRICING_ADMIN} authentication is established only for the
 * duration of the import call and restored afterward.
 */
@Component
public class DemoDataSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeedRunner.class);

    static final String PRODUCT_ENTITY_DEF_CODE = "Product";
    static final String CUSTOMER_ENTITY_DEF_CODE = "Customer";

    private static final String PRODUCTS_CSV_CLASSPATH_LOCATION = "demo-data/automotive/products.csv";
    private static final String CUSTOMERS_CSV_CLASSPATH_LOCATION = "demo-data/automotive/customers.csv";

    private final PackLoaderService packLoaderService;
    private final CatalogImportService catalogImportService;
    private final EntityInstanceRepository entityInstanceRepository;

    public DemoDataSeedRunner(
            PackLoaderService packLoaderService,
            CatalogImportService catalogImportService,
            EntityInstanceRepository entityInstanceRepository) {
        this.packLoaderService = packLoaderService;
        this.catalogImportService = catalogImportService;
        this.entityInstanceRepository = entityInstanceRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        packLoaderService.loadAllPacksFromClasspath();

        seedIfAbsent(PRODUCT_ENTITY_DEF_CODE, PRODUCTS_CSV_CLASSPATH_LOCATION);
        seedIfAbsent(CUSTOMER_ENTITY_DEF_CODE, CUSTOMERS_CSV_CLASSPATH_LOCATION);
    }

    private void seedIfAbsent(String entityDefCode, String classpathLocation) throws IOException {
        if (!entityInstanceRepository.findByEntityDefinition_Code(entityDefCode).isEmpty()) {
            log.info(
                    "Demo data for entity_def '{}' already present — skipping seed (idempotent restart,"
                            + " S2-T6)",
                    entityDefCode);
            return;
        }

        String csvContent = readClasspathCsv(classpathLocation);
        ImportResultDto result =
                asPricingAdmin(
                        () -> catalogImportService.importCsv(entityDefCode, classpathLocation, csvContent));
        log.info(
                "Seeded demo data for entity_def '{}': status={}, created={}, rowErrors={}",
                entityDefCode,
                result.status(),
                result.createdCount(),
                result.errors().size());
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
                        "demo-data-seed-runner",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_PRICING_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(seedRunnerAuthentication);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        }
    }
}
