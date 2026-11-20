package com.cpq.app.metamodel;

import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityDefinitionDimensionAttribute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link EntityDefinitionDimensionAttribute}
 * (ADR-009 mục 1, D-037/D-038/D-039, S3-T1). Lives in {@code app}, not
 * {@code core-metamodel} — same ADR-005 reasoning as
 * {@link EntityDefinitionRepository}.
 */
public interface EntityDefinitionDimensionAttributeRepository
        extends JpaRepository<EntityDefinitionDimensionAttribute, Long> {

    /**
     * Used by {@code PackLoaderService} (full-replace on each pack reload —
     * see its javadoc) and {@code EntityInstanceService} (to read back, in
     * order, the dimension attributes whose instance-level values must all
     * be present before an {@code EntityRole.EXTENSION} instance's
     * composite {@code businessKey} can be assembled, D-039).
     */
    List<EntityDefinitionDimensionAttribute> findByEntityDefinitionOrderByPositionAsc(
            EntityDefinition entityDefinition);
}
