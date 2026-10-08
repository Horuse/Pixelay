package dev.horuse.pixelay.data;

/**
 * Vertical anchor of a component box relative to its {@code pixelY}: the anchor point pins the
 * TOP/MIDDLE/BOTTOM edge of the box, like CSS {@code top/middle/bottom}. A TextDisplay always grows
 * upward from its entity position, so anchoring is done by shifting the spawn position down by the
 * block height.
 */
public enum VAlign {
    TOP,
    MIDDLE,
    BOTTOM;

    /**
     * @return downward offset (canvas px) from the anchor point to the box bottom edge — the
     *     position where a TextDisplay entity must be spawned
     */
    public double bottomOffset(double boxHeightPx) {
        return switch (this) {
            case TOP -> boxHeightPx;
            case MIDDLE -> boxHeightPx / 2;
            case BOTTOM -> 0;
        };
    }
}
