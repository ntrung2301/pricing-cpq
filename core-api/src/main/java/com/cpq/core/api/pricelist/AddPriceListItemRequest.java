package com.cpq.core.api.pricelist;

/**
 * Request body for {@code POST /api/price-lists-v2/{id}/items} (S3-T6):
 * the Product (core-metamodel {@code EntityInstance}) to price and add.
 */
public record AddPriceListItemRequest(Long entityInstanceId) {}
