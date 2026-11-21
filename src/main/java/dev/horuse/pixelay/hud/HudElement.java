package dev.horuse.pixelay.hud;

import net.kyori.adventure.text.Component;

/**
 * An on-screen HUD element anchored to a 2D screen anchor point.
 */
public interface HudElement {
    /**
     * Returns the target screen anchor.
     *
     * @return the screen anchor
     */
    HudAnchor anchor();

    /**
     * Returns the horizontal pixel offset from the anchor.
     *
     * @return the X offset in pixels
     */
    int offsetX();

    /**
     * Returns the vertical pixel offset from the anchor.
     *
     * @return the Y offset in pixels
     */
    int offsetY();

    /**
     * Renders this element into an Adventure text component.
     *
     * @return the rendered component
     */
    Component render();

    /**
     * Renders this element for a specific viewer.
     *
     * @param player viewer player
     * @return the rendered component
     */
    default Component renderFor(org.bukkit.entity.Player player) {
        return render();
    }
}
