package dev.horuse.pixelay.layout;

/**
 * Specifies overflow behavior when content extends outside box bounds.
 */
public enum Overflow {
    /** Renders content outside box boundaries. */
    VISIBLE,
    /** Clips content at box boundaries and blocks outside interaction. */
    HIDDEN,
    /** Clips content at box boundaries without scrollbars. */
    CLIP,
    /** Clips content and enables scrolling with visible scrollbars. */
    SCROLL,
    /** Clips content and enables scrolling when content overflows. */
    AUTO;

    public boolean clips() {
        return this != VISIBLE;
    }

    public boolean scrolls() {
        return this == SCROLL || this == AUTO;
    }
}
