package dev.horuse.pixelay.clicks;

import org.bukkit.entity.Player;

/** Something on a canvas that may handle a click at a canvas pixel. */
@FunctionalInterface
public interface ClickTarget {
    /** @return whether the click was handled (then no other target gets it) */
    boolean click(double pixelX, double pixelY, Player player);
}
