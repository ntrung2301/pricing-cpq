package com.cpq.core.metamodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A named constraint type definition (ADR-001/E1-02). {@code type} is a
 * generic taxonomy value core already names — e.g. {@code "compatibility"},
 * {@code "range"}, {@code "required-if"} — but core does not enumerate
 * every possible domain-specific instance of a type; it only knows the
 * type taxonomy, not what any given constraint *instance* checks. For
 * example, "fitment" (ADR-003) is just a {@code constraint_instance} of
 * the generic {@code "compatibility"} type, defined by a domain pack —
 * core never sees "fitment" as a concept.
 */
@Entity
@Table(name = "constraint_def")
public class ConstraintDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 50)
    private String type;

    protected ConstraintDefinition() {
        // JPA
    }

    public ConstraintDefinition(String code, String type) {
        this.code = code;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getType() {
        return type;
    }
}
