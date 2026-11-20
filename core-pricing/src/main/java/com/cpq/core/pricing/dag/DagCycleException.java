package com.cpq.core.pricing.dag;

/**
 * Thrown when a graph handed to {@link DagEngine#execute} is not actually
 * acyclic — topological sort (Kahn's algorithm) terminated before every
 * node was visited. ADR-009 requires this to fail loudly and immediately
 * rather than hang or silently compute a wrong order (no loop/recursion is
 * ever attempted on the cyclic portion).
 */
public class DagCycleException extends DagValidationException {

    public DagCycleException(String message) {
        super(message);
    }
}
