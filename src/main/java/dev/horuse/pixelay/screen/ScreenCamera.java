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
 * The locked camera of a {@link ScreenSession}: the spectator player rides an invisible entity and looks through
 * an invisible camera entity, and a packet-only Interaction around the camera makes the client send clicks. The
 * player rides the camera itself, or a seat above it (see {@link #ScreenCamera}). The session owns it and
 * decides the order things are restored in.
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
    private final double seatHeight;
    private final float lookYaw;
    private final int clickTargetId = FakeEntityIds.next();
    private ArmorStand anchor;
    /** What the player rides: the camera, or a seat {@link #seatHeight} above it. */
    private ArmorStand seat;
    private int ticks;
    private boolean removed;

    /**
     * @param seatHeight blocks above the camera the player sits (0 = on the camera): the shader cursor puts
     *     client-side water at the player's eyes, which must stay far out of the camera's view
     * @param lookYaw yaw angle the player's view starts facing
     */
    ScreenCamera(Player player, Location view, double seatHeight, float lookYaw) {
        this.player = player;
        this.view = view;
        this.seatHeight = seatHeight;
        this.lookYaw = lookYaw;
    }

    /** Spawns the camera entity and locks the player onto it a few ticks later. */
    void spawn() {
        spawn(null);
    }

    /** Spawns the camera entity, locks the player onto it, and runs {@code onMounted} once mounted. */
    void spawn(Runnable onMounted) {
        anchor = spawnStand(view);
        seat = seatHeight > 0 ? spawnStand(seatLocation()) : anchor;
        Bukkit.getScheduler().runTaskLater(
            Pixelay.plugin(),
            () -> {
                mount();
                if (onMounted != null) {
                    onMounted.run();
                }
            },
            MOUNT_DELAY_TICKS
        );
    }

    /** Where the player's vehicle stands. */
    Location seatLocation() {
        return view.clone().add(0, seatHeight, 0);
    }

    /** A marker armor stand: a valid camera target and vehicle with no model and no hitbox. */
    private ArmorStand spawnStand(Location at) {
        return at.getWorld().spawn(at, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setRotation(view.getYaw(), 0);
            DisplayUtils.tag(stand);
        });
    }

    boolean is(Entity entity) {
        return entity != null && (entity.equals(anchor) || entity.equals(seat));
    }

    /** Keeps the player on the camera; called every tick. */
    void tick() {
        if (anchor == null || !anchor.isValid()) {
            if (player.getLocation().distanceSquared(view) > 0.001) {
                player.teleport(view);
            }
            return;
        }
        if (player.getVehicle() != seat) {
            seat.addPassenger(player);
        }
        // After the switch to spectator the client resets entity tracking and may lose the camera.
        if (ticks++ < RESEND_TICKS) {
            sendClickTarget();
            PacketUtils.mount(player, seat);
            PacketUtils.sendCamera(player, anchor);
        }
    }

    /** After a spectator click the client moves its camera on its own: put it back now and next tick. */
    void reset() {
        if (anchor == null) {
            return;
        }
        PacketUtils.mount(player, seat);
        PacketUtils.sendCamera(player, anchor);
        Bukkit.getScheduler().runTask(Pixelay.plugin(), () -> {
            if (!usable()) {
                return;
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(anchor);
            }
            PacketUtils.mount(player, seat);
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
        if (seat != null && seat != anchor) {
            seat.eject();
            seat.remove();
        }
        seat = null;
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
        if (player.getVehicle() != seat && !seat.addPassenger(player)) {
            PacketUtils.mount(player, seat);
        }
        sendClickTarget();
        PacketUtils.sendCamera(player, anchor);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(anchor);
        }
        PacketUtils.sendLook(player, lookYaw, 0f);
    }

    private boolean usable() {
        return !removed && player.isOnline() && anchor != null && anchor.isValid() && seat != null && seat.isValid();
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
