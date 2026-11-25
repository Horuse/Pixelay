package dev.horuse.pixelay.hud;

/**
 * Screen anchor points for on-screen HUD elements in 2D GUI space.
 * Each anchor maps to an index decoded by the core vertex shader.
 */
public enum HudAnchor {
    /** Top-left screen corner at (0, 0). */
    TOP_LEFT(0),

    /** Top-center screen position at (width / 2, 0). */
    TOP_CENTER(1),

    /** Top-right screen corner at (width, 0). */
    TOP_RIGHT(2),

    /** Center-left screen position at (0, height / 2). */
    CENTER_LEFT(3),

    /** Center screen position at (width / 2, height / 2). */
    CENTER(4),

    /** Center-right screen position at (width, height / 2). */
    CENTER_RIGHT(5),

    /** Bottom-left screen corner at (0, height). */
    BOTTOM_LEFT(6),

    /** Bottom-center screen position at (width / 2, height). */
    BOTTOM_CENTER(7),

    /** Bottom-right screen corner at (width, height). */
    BOTTOM_RIGHT(8),

    /** Top-center compass tape with GPU sub-tick yaw interpolation. */
    COMPASS_TAPE(9),

    /** Left end of the hotbar at (width / 2 - 91, height). Content extends to the left. */
    HOTBAR_LEFT(10),

    /** Right end of the hotbar at (width / 2 + 91, height). Content extends to the right. */
    HOTBAR_RIGHT(11),

    /** Center of the hotbar top edge at (width / 2, height - 22). Content is centered and grows upward. */
    ABOVE_HOTBAR(12);

    /** Half of the vanilla hotbar width in GUI pixels. */
    public static final int HOTBAR_HALF_WIDTH = 91;

    /** Vanilla hotbar height in GUI pixels. */
    public static final int HOTBAR_HEIGHT = 22;

    private final int id;

    HudAnchor(int id) {
        this.id = id;
    }

    /**
     * Returns the anchor identifier.
     *
     * @return the integer anchor ID
     */
    public int id() {
        return id;
    }

    /**
     * Returns true if this anchor aligns text to the bottom screen edge.
     *
     * @return true for bottom alignment
     */
    public boolean isBottomAnchored() {
        return (
            this == BOTTOM_LEFT ||
            this == BOTTOM_CENTER ||
            this == BOTTOM_RIGHT ||
            this == HOTBAR_LEFT ||
            this == HOTBAR_RIGHT ||
            this == ABOVE_HOTBAR
        );
    }

    /**
     * Returns true if this anchor aligns text to the left screen edge.
     *
     * @return true for left alignment
     */
    public boolean isLeftAligned() {
        return this == TOP_LEFT || this == CENTER_LEFT || this == BOTTOM_LEFT || this == HOTBAR_RIGHT;
    }

    /**
     * Returns true if this anchor aligns text to the right screen edge.
     *
     * @return true for right alignment
     */
    public boolean isRightAligned() {
        return this == TOP_RIGHT || this == CENTER_RIGHT || this == BOTTOM_RIGHT || this == HOTBAR_LEFT;
    }

    /**
     * Returns true if this anchor centers text horizontally.
     *
     * @return true for center alignment
     */
    public boolean isCenterAligned() {
        return (
            this == TOP_CENTER ||
            this == CENTER ||
            this == BOTTOM_CENTER ||
            this == COMPASS_TAPE ||
            this == ABOVE_HOTBAR
        );
    }
}
