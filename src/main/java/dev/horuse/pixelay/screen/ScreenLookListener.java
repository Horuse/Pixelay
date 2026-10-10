package dev.horuse.pixelay.screen;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;
import org.bukkit.entity.Player;

/**
 * Feeds the camera look of players in a {@link ScreenSession} to their cursor. The server-side player never
 * rotates while locked on the camera, but the client keeps sending its look: in vehicle move packets while
 * riding the camera, in rotation packets otherwise.
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
        }
    }
}
