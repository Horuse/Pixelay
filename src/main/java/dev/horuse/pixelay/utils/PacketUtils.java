package dev.horuse.pixelay.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Thin facade over PacketEvents for sending server -> client packets. Keep packet-building helpers
 * here so gameplay code stays wrapper-agnostic.
 */
public final class PacketUtils {

    private PacketUtils() {}

    /** Sends any packet wrapper to a single player. */
    public static void send(Player player, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    /**
     * Nudges the client's camera by {@code yaw}/{@code pitch} degrees (relative deltas) without
     * moving it. Uses the dedicated Player Rotation packet (1.21.2+), which — unlike a position
     * teleport — carries no teleport id, so it needs no client confirmation and never desyncs the
     * player's position. The client keeps full authority over movement: no rubber-banding, no kick.
     */
    public static void sendRelativeLook(Player player, float yaw, float pitch) {
        send(player, new WrapperPlayServerPlayerRotation(yaw, true, pitch, true));
    }

    /** Locks the player's spectator camera onto the given entity (game mode 3). */
    public static void sendCamera(Player player, Entity camera) {
        send(player, new WrapperPlayServerCamera(camera.getEntityId()));
    }

    /**
     * Makes the player ride the given entity. Riding makes the client keep sending rotation
     * (yaw/pitch) packets, which the server needs to track the spectator camera look.
     */
    public static void mount(Player player, Entity holder) {
        send(player, new WrapperPlayServerSetPassengers(holder.getEntityId(), new int[] { player.getEntityId() }));
    }
}
