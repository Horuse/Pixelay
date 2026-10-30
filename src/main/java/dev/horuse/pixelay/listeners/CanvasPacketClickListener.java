package dev.horuse.pixelay.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.clicks.CanvasClickCatcher;
import dev.horuse.pixelay.clicks.CanvasClicks;
import org.bukkit.entity.Player;

/**
 * Routes screen clicks through client network packets in SPECTATOR mode.
 * In spectator mode, LMB does not send INTERACT_ENTITY or ANIMATION.
 * The client changes the camera locally to the entity under crosshairs or to itself, and sends Spectate to its UUID.
 * The listener intercepts both options as clicks, cancels the packet, and restores the camera anchor.
 * The listener also monitors and blocks dismount attempts (Shift) at the PLAYER_INPUT level.
 */
public class CanvasPacketClickListener implements PacketListener {

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPlayer() instanceof Player player && CanvasClickCatcher.isViewer(player)) {
            // Tracks and blocks dismount attempts (Shift) to keep passenger attached to the anchor.
            if (event.getPacketType() == PacketType.Play.Client.PLAYER_INPUT) {
                WrapperPlayClientPlayerInput packet = new WrapperPlayClientPlayerInput(event);

                if (!packet.isShift()) {
                    CanvasClickCatcher.setSneaking(player, false);
                } else {
                    CanvasClickCatcher.setSneaking(player, true);
                    if (Pixelay.settings().clicks().blockSneakDismount()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            // In spectator mode, LMB sends Spectate. With a packet-only entity, the client can send INTERACT_ENTITY or ATTACK.
            // All these packets represent a screen click under the cursor.
            if (
                event.getPacketType() == PacketType.Play.Client.SPECTATE ||
                event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY ||
                event.getPacketType() == PacketType.Play.Client.ATTACK
            ) {
                routeScreenClick(event, player);
                return;
            }

            // LMB in the air sends ANIMATION.
            if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
                if (CanvasClickCatcher.handleSwing(player)) {
                    Pixelay.debug("click ANIMATION routed");
                }
                return;
            }

            // LMB on blocks sends PLAYER_DIGGING with START_DIGGING.
            if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
                WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
                if (packet.getAction() == DiggingAction.START_DIGGING && CanvasClickCatcher.handleSwing(player)) {
                    Pixelay.debug("click PLAYER_DIGGING routed");
                }
                event.setCancelled(true);
                return;
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            WrapperPlayClientAttack packet = new WrapperPlayClientAttack(event);
            handleClick(event, packet.getEntityId());
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            handleClick(event, packet.getEntityId());
        }
    }

    /**
     * Intercepts a screen click packet, routes the click if not sneaking, and restores the camera.
     */
    private static void routeScreenClick(PacketReceiveEvent event, Player player) {
        event.setCancelled(true);
        boolean sneaking = CanvasClickCatcher.isSneaking(player) || player.isSneaking();
        if (!sneaking && CanvasClickCatcher.handleSwing(player)) {
            Pixelay.debug("click " + event.getPacketType() + " routed");
        }
        CanvasClickCatcher.resetCamera(player);
    }

    private void handleClick(PacketReceiveEvent event, int entityId) {
        if (event.getPlayer() instanceof Player player && CanvasClicks.onEntityClick(entityId, player)) {
            event.setCancelled(true);
        }
    }
}
