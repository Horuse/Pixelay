package dev.horuse.pixelay.layout;

/**
 * CSS {@code text-overflow} behaviour when single-line text exceeds its available width.
 */
public enum TextOverflow {
    /** Cuts text at the box edge (CSS {@code text-overflow: clip}). */
    CLIP,
    /** Appends an ellipsis ({@code "..."}) to represent clipped text (CSS {@code text-overflow: ellipsis}). */
    ELLIPSIS,
}
