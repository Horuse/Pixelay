package dev.horuse.pixelay.data;

/**
 * Specifies vertical anchor alignment of a component box.
 * The anchor point aligns with the top, middle, or bottom edge of the box.
 * The system calculates downward offset to position the display entity.
 */
public enum VAlign {
    TOP,
    MIDDLE,
    BOTTOM;

    /**
     * @return downward pixel offset from anchor to box bottom edge
     */
    public double bottomOffset(double boxHeightPx) {
        return switch (this) {
            case TOP -> boxHeightPx;
            case MIDDLE -> boxHeightPx / 2;
            case BOTTOM -> 0;
        };
    }
}
