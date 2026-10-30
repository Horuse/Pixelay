package dev.horuse.pixelay.screen;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;
import org.bukkit.entity.Player;

/**
 * Routes camera look packets to the session cursor, and routes mouse wheel or movement keys to scrolling.
 */
public class ScreenLookListener implements PacketListener {

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        ScreenSession session = ScreenSession.of(player).orElse(null);
        if (session == null) {
            return;
        }
        if (event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE) {
            WrapperPlayClientVehicleMove packet = new WrapperPlayClientVehicleMove(event);
            session.look(packet.getYaw(), packet.getPitch());
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION) {
            WrapperPlayClientPlayerRotation packet = new WrapperPlayClientPlayerRotation(event);
            session.look(packet.getYaw(), packet.getPitch());
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            WrapperPlayClientPlayerPositionAndRotation packet = new WrapperPlayClientPlayerPositionAndRotation(event);
            session.look(packet.getYaw(), packet.getPitch());
        } else if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            WrapperPlayClientHeldItemChange packet = new WrapperPlayClientHeldItemChange(event);
            session.heldItemChange(packet.getSlot());
        } else if (event.getPacketType() == PacketType.Play.Client.STEER_VEHICLE) {
            WrapperPlayClientSteerVehicle packet = new WrapperPlayClientSteerVehicle(event);
            if (packet.getForward() > 0.1f) {
                session.scroll(-1);
            } else if (packet.getForward() < -0.1f) {
                session.scroll(1);
            }
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_INPUT) {
            WrapperPlayClientPlayerInput packet = new WrapperPlayClientPlayerInput(event);
            if (packet.isForward()) {
                session.scroll(-1);
            } else if (packet.isBackward()) {
                session.scroll(1);
            }
        }
    }
}
