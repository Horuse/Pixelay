package dev.horuse.pixelay.screen;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Keeps {@link ScreenSession}s locked on their camera and cleans up after players who leave. */
public class ScreenListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && ScreenSession.isCamera(player, event.getDismounted())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        if (ScreenSession.isInSession(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ScreenSession.onQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ScreenSession.onJoin(event.getPlayer());
    }
}
