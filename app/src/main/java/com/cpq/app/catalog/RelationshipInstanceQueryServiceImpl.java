package com.cpq.app.catalog;

import com.cpq.app.metamodel.RelationshipInstanceRepository;
import com.cpq.core.api.catalog.RelationshipInstanceQueryService;
import com.cpq.core.api.catalog.SupersedingInstanceSummary;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.RelationshipInstance;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code app}-side implementation of {@code core-api}'s
 * {@link RelationshipInstanceQueryService} port (S2-T7), same
 * interface-in-core-api/implementation-in-app split as
 * {@code com.cpq.app.configurator.ConfiguratorServiceImpl} (S2-T8).
 *
 * <p>Entirely generic: it reads {@link RelationshipInstance} rows by
 * relationship_def <em>code</em> — the {@code "supersedes"} literal below
 * is a generic taxonomy value the automotive domain pack happens to name
 * this way (same reasoning {@code ConfiguratorServiceImpl} already
 * documented for hardcoding {@code "compatibility"}, D-027 point 4: this
 * is the API's business-facing contract, not a core concept). This class
 * never looks at what entity_def the "from"/"to" sides actually are.
 */
@Service
public class RelationshipInstanceQueryServiceImpl implements RelationshipInstanceQueryService {

    static final String SUPERSEDES_RELATIONSHIP_CODE = "supersedes";

    private final RelationshipInstanceRepository relationshipInstanceRepository;

    public RelationshipInstanceQueryServiceImpl(RelationshipInstanceRepository relationshipInstanceRepository) {
        this.relationshipInstanceRepository = relationshipInstanceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupersedingInstanceSummary> findSupersededBy(Long instanceId) {
        return relationshipInstanceRepository
                .findByRelationshipDefinitionCodeAndToInstanceId(SUPERSEDES_RELATIONSHIP_CODE, instanceId)
                .stream()
                .map(RelationshipInstanceQueryServiceImpl::toSummary)
                .toList();
    }

    private static SupersedingInstanceSummary toSummary(RelationshipInstance relationshipInstance) {
        EntityInstance from = relationshipInstance.getFromInstance();
        return new SupersedingInstanceSummary(from.getId(), from.getBusinessKey(), from.getDisplayLabel());
    }
}
