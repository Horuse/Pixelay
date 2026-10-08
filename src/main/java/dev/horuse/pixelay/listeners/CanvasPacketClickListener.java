package dev.horuse.pixelay.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSpectate;
import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.components.CanvasButton;
import dev.horuse.pixelay.components.CanvasClickCatcher;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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

    private static final Set<UUID> SNEAKING = ConcurrentHashMap.newKeySet();

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPlayer() instanceof Player player && CanvasClickCatcher.isViewer(player)) {
            // Track and block dismount attempts (Shift): otherwise the server sees a dismount
            // request and detaches the passenger from the camera anchor.
            if (event.getPacketType() == PacketType.Play.Client.PLAYER_INPUT) {
                WrapperPlayClientPlayerInput packet = new WrapperPlayClientPlayerInput(event);

                if (!packet.isShift()) {
                    SNEAKING.remove(player.getUniqueId());
                } else {
                    SNEAKING.add(player.getUniqueId());
                    if (Pixelay.settings().clicks().blockSneakDismount()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            // LMB in spectator mode sends Spectate: UUID of the entity under the crosshair (Interaction)
            // or its own UUID (clicking past the entity). Both = click on the screen under the cursor.
            if (event.getPacketType() == PacketType.Play.Client.SPECTATE) {
                WrapperPlayClientSpectate packet = new WrapperPlayClientSpectate(event);
                event.setCancelled(true);
                boolean sneaking = SNEAKING.contains(player.getUniqueId()) || player.isSneaking();

                if (!sneaking && CanvasClickCatcher.handleSwing(player)) {
                    Pixelay.debug("click SPECTATE " + packet.getTargetUUID() + " routed");
                }

                // The client has already locally switched the camera — forcibly return it to the anchor,
                // otherwise the client's spectator state will desync from the server.
                CanvasClickCatcher.resetCamera(player);
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

                if (packet.getAction() == DiggingAction.START_DIGGING) {
                    if (CanvasClickCatcher.handleSwing(player)) {
                        Pixelay.debug("click PLAYER_DIGGING routed");
                    }
                }

                event.setCancelled(true);
                return;
            }

            // For packet (fake) entities, the client in spectator mode can send INTERACT_ENTITY
            // instead of SPECTATE — intercept as a click on the screen.
            if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                event.setCancelled(true);
                boolean sneaking = SNEAKING.contains(player.getUniqueId()) || player.isSneaking();

                if (!sneaking && CanvasClickCatcher.handleSwing(player)) {
                    WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                    Pixelay.debug("click INTERACT_ENTITY id=" + packet.getEntityId() + " routed");
                }

                CanvasClickCatcher.resetCamera(player);
                return;
            }

            if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
                event.setCancelled(true);
                boolean sneaking = SNEAKING.contains(player.getUniqueId()) || player.isSneaking();

                if (!sneaking && CanvasClickCatcher.handleSwing(player)) {
                    WrapperPlayClientAttack packet = new WrapperPlayClientAttack(event);
                    Pixelay.debug("click ATTACK id=" + packet.getEntityId() + " routed");
                }

                CanvasClickCatcher.resetCamera(player);
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

    private void handleClick(PacketReceiveEvent event, int entityId) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Consumer<Player> callback = CanvasButton.getCallbackById(entityId, player);

        if (callback == null) {
            return;
        }

        Pixelay.debug("click " + event.getPacketType() + " id=" + entityId);
        callback.accept(player);
        event.setCancelled(true);
    }
}
