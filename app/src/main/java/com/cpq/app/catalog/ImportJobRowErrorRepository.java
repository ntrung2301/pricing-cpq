package com.cpq.app.catalog;

import com.cpq.core.catalog.ImportJobRowError;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ImportJobRowError} (S2-T5). Lives
 * in {@code app} — same ADR-005 reasoning as {@link ImportJobRepository}.
 */
public interface ImportJobRowErrorRepository extends JpaRepository<ImportJobRowError, Long> {}
