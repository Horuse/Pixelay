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
    COMPASS_TAPE(9);

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
}
