package dev.horuse.pixelay.debug;

import dev.horuse.pixelay.clicks.CanvasClicks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Drops players who left from the layout debug mode, so server-wide highlights do not stay on. */
public class LayoutDebugListener implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        LayoutDebug.remove(event.getPlayer().getUniqueId());
        CanvasClicks.remove(event.getPlayer());
        CanvasBoundsDebug.remove(event.getPlayer().getUniqueId());
    }
}
