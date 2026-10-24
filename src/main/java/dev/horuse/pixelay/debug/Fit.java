package dev.horuse.pixelay.debug;

/** Whether an element fits the space its layout gave it. */
public enum Fit {
    /** Fits. */
    NONE,
    /** Bigger than its own space: this is where the problem is. */
    SELF,
    /** Fits itself, but sits inside a box that overflows. */
    INSIDE,
}
