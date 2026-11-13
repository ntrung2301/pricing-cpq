package com.cpq.app.pack;

/**
 * Thrown by {@link PackLoaderService} when a {@code pack.yaml} fails
 * validation (e.g. an entity declares more than 20 attributes — D-019 —
 * or a relationship/key/label attribute references a code that doesn't
 * exist). The whole pack is rejected — nothing from it is persisted
 * (loading runs inside one transaction).
 */
public class PackLoadException extends RuntimeException {

    public PackLoadException(String message) {
        super(message);
    }
}
