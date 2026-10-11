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
 * Routes screen clicks through client network packets in SPECTATOR mode. In spectator mode, LMB
 * does not send INTERACT_ENTITY/ANIMATION — the client locally switches the camera to the entity
 * under the crosshair (fake Interaction in front of the screen) or to itself (clicking in the air)
 * and sends Spectate to its UUID. Both options are intercepted as a click, the packet is cancelled,
 * and the camera is forcibly returned to the anchor. Attempts to dismount (Shift) are tracked and
 * blocked at the PLAYER_INPUT level ({@code clicks.block-sneak-dismount}), so that a spectator
 * detach via Shift is not considered a click.
 */
public class CanvasPacketClickListener implements PacketListener {

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPlayer() instanceof Player player && CanvasClickCatcher.isViewer(player)) {
            // Track and block dismount attempts (Shift): otherwise the server sees a dismount
            // request and detaches the passenger from the camera anchor.
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

            // LMB in spectator mode sends Spectate (the entity under the crosshair, or the player's own UUID
            // when clicking past it). With a packet-only entity under the crosshair the client may send
            // INTERACT_ENTITY or ATTACK instead. All of them are a click on the screen under the cursor.
            if (
                event.getPacketType() == PacketType.Play.Client.SPECTATE ||
                event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY ||
                event.getPacketType() == PacketType.Play.Client.ATTACK
            ) {
                routeScreenClick(event, player);
                return;
            }

            // LMB in the air sends ANIMATION
            if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
                if (CanvasClickCatcher.handleSwing(player)) {
                    Pixelay.debug("click ANIMATION routed");
                }
                return;
            }

            // LMB on blocks sends PLAYER_DIGGING (START_DIGGING)
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
     * A screen click packet: cancelled, routed as a click unless the player holds Shift (that is an attempt
     * to leave the camera), and the camera put back — the client has already moved it on its own.
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
