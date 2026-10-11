package dev.horuse.pixelay.screen;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.utils.DisplayUtils;
import dev.horuse.pixelay.utils.FakeEntityIds;
import dev.horuse.pixelay.utils.PacketUtils;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * The locked camera of a {@link ScreenSession}: the spectator player rides an invisible camera entity and
 * looks through it, and a packet-only Interaction around it makes the client send clicks. The session owns
 * it and decides the order things are restored in.
 */
final class ScreenCamera {

    /** Ticks after opening during which the camera packets are re-sent (the client resets after a game mode change). */
    private static final int RESEND_TICKS = 10;
    /** Delay before mounting: the client must finish switching to spectator first. */
    private static final long MOUNT_DELAY_TICKS = 5;
    /** Size (blocks) of the click catcher box around the camera. */
    private static final float CLICK_TARGET_SIZE = 1.5f;

    private final Player player;
    private final Location view;
    private final int clickTargetId = FakeEntityIds.next();
    private ArmorStand anchor;
    private int ticks;
    private boolean removed;

    ScreenCamera(Player player, Location view) {
        this.player = player;
        this.view = view;
    }

    /** Spawns the camera entity and locks the player onto it a few ticks later. */
    void spawn() {
        // A marker armor stand is a valid camera target with no model and no hitbox.
        anchor = view.getWorld().spawn(view, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setRotation(view.getYaw(), 0);
            DisplayUtils.tag(stand);
        });
        Bukkit.getScheduler().runTaskLater(Pixelay.plugin(), this::mount, MOUNT_DELAY_TICKS);
    }

    boolean is(Entity entity) {
        return anchor != null && anchor.equals(entity);
    }

    /** Keeps the player on the camera; called every tick. */
    void tick() {
        if (anchor == null || !anchor.isValid()) {
            if (player.getLocation().distanceSquared(view) > 0.001) {
                player.teleport(view);
            }
            return;
        }
        if (player.getVehicle() != anchor) {
            anchor.addPassenger(player);
        }
        // After the switch to spectator the client resets entity tracking and may lose the camera.
        if (ticks++ < RESEND_TICKS) {
            sendClickTarget();
            PacketUtils.mount(player, anchor);
            PacketUtils.sendCamera(player, anchor);
        }
    }

    /** After a spectator click the client moves its camera on its own: put it back now and next tick. */
    void reset() {
        if (anchor == null) {
            return;
        }
        PacketUtils.mount(player, anchor);
        PacketUtils.sendCamera(player, anchor);
        Bukkit.getScheduler().runTask(Pixelay.plugin(), () -> {
            if (!usable()) {
                return;
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(anchor);
            }
            PacketUtils.mount(player, anchor);
            PacketUtils.sendCamera(player, anchor);
        });
    }

    /** Gives the player their own view back and removes the camera. */
    void remove() {
        removed = true;
        if (player.isOnline()) {
            PacketUtils.destroy(player, clickTargetId);
            PacketUtils.sendCamera(player, player);
        }
        if (anchor != null) {
            anchor.eject();
            anchor.remove();
            anchor = null;
        }
    }

    private void mount() {
        if (!usable()) {
            return;
        }
        // A real server-side ride: Paper drops the client's vehicle look packets from non-passengers.
        if (player.getVehicle() != anchor && !anchor.addPassenger(player)) {
            PacketUtils.mount(player, anchor);
        }
        sendClickTarget();
        PacketUtils.sendCamera(player, anchor);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(anchor);
        }
    }

    private boolean usable() {
        return !removed && player.isOnline() && anchor != null && anchor.isValid();
    }

    /**
     * A packet-only Interaction around the camera: a spectator's click is only sent when an entity is under
     * the crosshair, and the client counts an entity it looks out of as always under it (an entity in front
     * would also have to be within reach).
     */
    private void sendClickTarget() {
        float size = CLICK_TARGET_SIZE;
        UUID uuid = UUID.nameUUIDFromBytes(("pixelay-screen-" + player.getUniqueId()).getBytes());
        PacketUtils.spawnInteraction(
            player,
            clickTargetId,
            uuid,
            view.getX(),
            view.getY() - size / 2.0,
            view.getZ(),
            size,
            size
        );
    }
}
