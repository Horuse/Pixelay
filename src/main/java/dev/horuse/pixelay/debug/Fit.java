package dev.horuse.pixelay.debug;

/** Indicates whether an element fits allocated layout space. */
public enum Fit {
    /** Element fits within its bounds. */
    NONE,
    /** Element exceeds its allocated bounds. */
    SELF,
    /** Element fits its own bounds, but ancestor overflows. */
    INSIDE,
}
