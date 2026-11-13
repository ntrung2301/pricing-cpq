package com.cpq.app.catalog;

import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one catalog-import CSV row as a single, atomic unit (S2-T5): one
 * {@link EntityInstance} plus every one of its attribute values. Deliberately
 * its own {@code @Service} bean (not a method on {@code
 * CatalogImportServiceImpl}) so that each call goes through Spring's
 * transactional proxy — {@code CatalogImportServiceImpl} itself is *not*
 * {@code @Transactional}, so each row gets its own, independent
 * transaction. If any attribute write fails partway through a row (bad
 * value type already filtered out upstream, but e.g. a duplicate
 * business key unique-constraint violation on
 * {@link EntityInstanceService#writeAttributeValue}), this method's
 * transaction rolls back *that row only* — the instance row and any
 * attribute values already written for it — while rows already committed
 * earlier in the same import, and rows still to come, are unaffected
 * (CLAUDE.md / sprint-2.md: "không crash cả job").
 */
@Service
public class CatalogRowWriter {

    private final EntityInstanceService entityInstanceService;

    public CatalogRowWriter(EntityInstanceService entityInstanceService) {
        this.entityInstanceService = entityInstanceService;
    }

    @Transactional
    public void writeRow(
            EntityDefinition entityDefinition,
            List<AttributeDefinition> attributes,
            Map<String, AttributeValue> valuesByAttributeCode,
            String packId,
            int packVersion) {
        EntityInstance instance = entityInstanceService.createInstance(entityDefinition, packId, packVersion);
        for (AttributeDefinition attribute : attributes) {
            AttributeValue value = valuesByAttributeCode.get(attribute.getCode());
            if (value != null) {
                entityInstanceService.writeAttributeValue(instance, attribute, value);
            }
        }
    }
}
