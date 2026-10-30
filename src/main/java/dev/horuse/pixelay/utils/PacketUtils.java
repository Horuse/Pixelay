package dev.horuse.pixelay.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBundle;
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
 * Utility methods for sending packets from server to client through PacketEvents.
 */
public final class PacketUtils {

    private PacketUtils() {}

    /** Spawns a packet-only entity with zero rotation and velocity. */
    public static void spawnEntity(Player viewer, int id, EntityType type, double x, double y, double z) {
        spawnEntity(viewer, id, UUID.randomUUID(), type, x, y, z);
    }

    /** Spawns a packet-only entity with a specified UUID. */
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

    /** Spawns a packet-only interaction entity with bottom center at the given coordinates. */
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

    /** Spawns a packet-only interaction entity with a specified UUID. */
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

    /** Sends packet wrapper to a player. */
    public static void send(Player player, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    /**
     * Sets client camera rotation to absolute yaw and pitch degrees.
     */
    public static void sendLook(Player player, float yaw, float pitch) {
        send(player, new WrapperPlayServerPlayerRotation(yaw, false, pitch, false));
    }

    /** Sends a bundle delimiter packet to process subsequent packets atomically. */
    public static void sendBundle(Player player) {
        send(player, new WrapperPlayServerBundle());
    }

    /**
     * Updates client camera rotation with relative yaw and pitch offsets without movement.
     */
    public static void sendRelativeLook(Player player, float yaw, float pitch) {
        send(player, new WrapperPlayServerPlayerRotation(yaw, true, pitch, true));
    }

    /** Sets the player spectator camera target to an entity. */
    public static void sendCamera(Player player, Entity camera) {
        send(player, new WrapperPlayServerCamera(camera.getEntityId()));
    }

    /**
     * Attaches player as passenger to entity to receive client rotation packets.
     */
    public static void mount(Player player, Entity holder) {
        send(player, new WrapperPlayServerSetPassengers(holder.getEntityId(), new int[] { player.getEntityId() }));
    }
}
