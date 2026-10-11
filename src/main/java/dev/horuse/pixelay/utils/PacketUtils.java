package dev.horuse.pixelay.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.config.PixelaySettings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Thin facade over PacketEvents for sending server -> client packets. Keep packet-building helpers
 * here so gameplay code stays wrapper-agnostic.
 */
public final class PacketUtils {

    private PacketUtils() {}

    /** Spawns a packet-only entity for one player, with no rotation or velocity. */
    public static void spawnEntity(Player viewer, int id, EntityType type, double x, double y, double z) {
        spawnEntity(viewer, id, UUID.randomUUID(), type, x, y, z);
    }

    /** Same, with a fixed UUID: re-sending it then updates the same client entity. */
    public static void spawnEntity(Player viewer, int id, UUID uuid, EntityType type, double x, double y, double z) {
        send(
            viewer,
            new WrapperPlayServerSpawnEntity(
                id,
                Optional.of(uuid),
                type,
                new Vector3d(x, y, z),
                0f,
                0f,
                0f,
                0,
                Optional.empty()
            )
        );
    }

    /** Spawns a packet-only Interaction box for one player: its bottom centre at {@code (x, y, z)}. */
    public static void spawnInteraction(
        Player viewer,
        int id,
        double x,
        double y,
        double z,
        float width,
        float height
    ) {
        spawnInteraction(viewer, id, UUID.randomUUID(), x, y, z, width, height);
    }

    /** Same, with a fixed UUID: re-sending it then updates the same client entity. */
    public static void spawnInteraction(
        Player viewer,
        int id,
        UUID uuid,
        double x,
        double y,
        double z,
        float width,
        float height
    ) {
        spawnEntity(viewer, id, uuid, EntityTypes.INTERACTION, x, y, z);
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        send(
            viewer,
            new WrapperPlayServerEntityMetadata(
                id,
                List.of(
                    new EntityData<>(idx.interactionWidth(), EntityDataTypes.FLOAT, width),
                    new EntityData<>(idx.interactionHeight(), EntityDataTypes.FLOAT, height)
                )
            )
        );
    }

    public static void destroy(Player viewer, int... ids) {
        if (viewer != null && viewer.isOnline() && ids.length > 0) {
            send(viewer, new WrapperPlayServerDestroyEntities(ids));
        }
    }

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
