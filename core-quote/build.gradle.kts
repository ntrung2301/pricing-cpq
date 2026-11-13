// core-quote: quote/quote_version/quote_line, approval workflow (E4-*).
// Standalone at skeleton stage (does NOT depend on core-catalog, even
// though a quote line references catalog items conceptually — that
// relationship is data (entity_instance ids), not a Java dependency).
plugins {
    id("cpq.java-conventions")
}
