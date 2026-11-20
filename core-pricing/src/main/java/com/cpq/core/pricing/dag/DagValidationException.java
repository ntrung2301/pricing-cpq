package com.cpq.core.pricing.dag;

/**
 * Thrown when a DAG graph or a node's config/inputs is structurally
 * invalid: an unknown node type, a missing required config field, an
 * input referencing a node id that doesn't exist in the graph, or a node
 * executor rejecting the shape of its resolved inputs. Always a clear,
 * named error — ADR-009 explicitly rules out silent fallback behaviour
 * for a malformed graph (same "sai thì báo lỗi rõ, không tự đoán" spirit
 * as D-005/ADR-009 mục 3's binding ambiguity rule).
 */
public class DagValidationException extends RuntimeException {

    public DagValidationException(String message) {
        super(message);
    }

    public DagValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
