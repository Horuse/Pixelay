package dev.horuse.pixelay.layout;

/**
 * CSS {@code overflow} of a box: whether what its children draw outside it is shown. Set it with
 * {@link Box#overflow}, or per axis with {@link Box#overflowX} / {@link Box#overflowY}. A canvas always
 * clips: nothing outside it is drawn, hovered or clicked.
 */
public enum Overflow {
    /** Children are drawn wherever they are (the CSS default). */
    VISIBLE,
    /** Children are cut at the box's edges (padding included), and are not hovered or clicked outside it. */
    HIDDEN,
    /** The same as {@link #HIDDEN}: there is no scrolling to tell them apart. */
    CLIP;

    boolean clips() {
        return this != VISIBLE;
    }
}
