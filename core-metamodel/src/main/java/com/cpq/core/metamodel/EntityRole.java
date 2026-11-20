package com.cpq.core.metamodel;

/**
 * Entity taxonomy v2 (ADR-009 mục 1, amending ADR-001; D-037/D-038/D-039,
 * S3-T1). Core only knows this generic 3-way classification — it never
 * knows what a given {@link EntityRole#EXTENSION}/{@link
 * EntityRole#PARAMETER} entity definition's business content represents.
 *
 * <ul>
 *   <li>{@link #ANCHOR} — exactly {@code Product} and {@code Customer} in
 *       every deployment, created once by {@code CoreAnchorBootstrapService}
 *       (never by a domain pack). Ngang hàng khái niệm core chính thức
 *       "price list"/"quote".</li>
 *   <li>{@link #EXTENSION} — a generalisation of ADR-009's
 *       {@code ProductExtension}/{@code CustomerExtension} categories: an
 *       entity definition that extends exactly one other entity definition
 *       (usually, but not necessarily, an {@link #ANCHOR}) and whose
 *       instances' business key is the extended instance's business key
 *       plus one or more ordered "dimension" attribute values (see
 *       {@link EntityDefinitionDimensionAttribute}) — e.g. {@code
 *       CostHistory} extending {@code Product} with dimension {@code
 *       period}.</li>
 *   <li>{@link #PARAMETER} — an independent entity definition with its own
 *       key (D-019's {@code keyAttributeDefinition}), unrelated to any
 *       anchor — e.g. {@code Vehicle}, {@code MarginThreshold}.</li>
 * </ul>
 *
 * <p>{@code role} is nullable on {@link EntityDefinition} (not every
 * entity definition declares one — see {@code PackLoaderService}'s
 * "giữ nguyên hành vi cũ nếu pack không khai báo role" rule, D-039).
 */
public enum EntityRole {
    ANCHOR,
    EXTENSION,
    PARAMETER
}
