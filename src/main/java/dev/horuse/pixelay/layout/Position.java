package dev.horuse.pixelay.layout;

/**
 * CSS {@code position} scheme of a {@link Box}.
 * <ul>
 *     <li>{@link #STATIC} — normal flow (default).</li>
 *     <li>{@link #RELATIVE} — laid out in normal flow, then offset by {@code top}/{@code right}/{@code bottom}/{@code left}.</li>
 *     <li>{@link #ABSOLUTE} — removed from normal flow and positioned relative to its parent container using {@code top}/{@code right}/{@code bottom}/{@code left}.</li>
 * </ul>
 */
public enum Position {
    STATIC,
    RELATIVE,
    ABSOLUTE,
}
