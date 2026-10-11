package dev.horuse.pixelay.listeners;

import dev.horuse.pixelay.clicks.CanvasClickCatcher;
import dev.horuse.pixelay.clicks.CanvasClicks;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.debug.CanvasBoundsDebug;
import dev.horuse.pixelay.debug.LayoutDebug;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Forgets a player who left in every per-player registry: debug modes, clicks, layers, Shift state. */
public class PlayerQuitCleanup implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        LayoutDebug.remove(event.getPlayer().getUniqueId());
        CanvasClicks.remove(event.getPlayer());
        CanvasBoundsDebug.remove(event.getPlayer().getUniqueId());
        Layers.forget(event.getPlayer().getUniqueId());
        CanvasClickCatcher.forget(event.getPlayer().getUniqueId());
    }
}
