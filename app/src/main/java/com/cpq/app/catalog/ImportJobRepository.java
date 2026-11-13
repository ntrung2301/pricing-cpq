package com.cpq.app.catalog;

import com.cpq.core.catalog.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ImportJob} (S2-T5). Lives in
 * {@code app}, not {@code core-catalog} — same ADR-005 reasoning as
 * {@code com.cpq.app.metamodel.EntityDefinitionRepository}.
 */
public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {}
