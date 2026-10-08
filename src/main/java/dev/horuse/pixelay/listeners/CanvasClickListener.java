package dev.horuse.pixelay.listeners;

import dev.horuse.pixelay.components.CanvasButton;
import java.util.function.Consumer;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

/**
 * Routes clicks on canvas {@link Interaction} hitboxes to their registered callbacks.
 * Domain-agnostic: it knows nothing about maps or areas.
 */
public class CanvasClickListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) {
            return;
        }

        Consumer<Player> callback = CanvasButton.getCallback(interaction.getUniqueId());

        if (callback == null) {
            return;
        }

        callback.accept(event.getPlayer());
        event.setCancelled(true);
    }
}
