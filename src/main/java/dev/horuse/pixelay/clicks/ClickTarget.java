package dev.horuse.pixelay.clicks;

import org.bukkit.entity.Player;

/** Handles a click at canvas pixel coordinates. */
@FunctionalInterface
public interface ClickTarget {
    /** @return true if this target handled the click, false otherwise */
    boolean click(double pixelX, double pixelY, Player player);
}
