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
 * Manages the locked camera of a {@link ScreenSession}.
 * The spectator player rides an invisible vehicle entity and views through an invisible camera entity.
 * A packet-only interaction entity around the camera makes the client send click events.
 * The session owns the camera and controls the restoration order.
 */
final class ScreenCamera {

    /** Ticks after opening during which camera packets are resent after game mode changes. */
    private static final int RESEND_TICKS = 10;
    /** Delay in ticks before mounting to allow client game mode transitions to finish. */
    private static final long MOUNT_DELAY_TICKS = 2;
    /** Size in blocks of the click target box around the camera. */
    private static final float CLICK_TARGET_SIZE = 1.5f;

    private final Player player;
    private final Location view;
    private final double seatHeight;
    private final float lookYaw;
    private final int clickTargetId = FakeEntityIds.next();
    private ArmorStand anchor;
    /** Vehicle entity that the player rides (the camera or an offset seat). */
    private ArmorStand seat;
    private int ticks;
    private boolean removed;

    /**
     * @param seatHeight Blocks above the camera where the player sits (0 sits on the camera).
     * @param lookYaw Initial yaw angle for the player view.
     */
    ScreenCamera(Player player, Location view, double seatHeight, float lookYaw) {
        this.player = player;
        this.view = view;
        this.seatHeight = seatHeight;
        this.lookYaw = lookYaw;
    }

    /** Spawns the camera entity and locks the player onto it. */
    void spawn() {
        spawn(null);
    }

    /** Spawns the camera entity, locks the player, and executes {@code onMounted} when complete. */
    void spawn(Runnable onMounted) {
        anchor = spawnStand(view);
        seat = seatHeight > 0 ? spawnStand(seatLocation()) : anchor;
        mount();
        Bukkit.getScheduler().runTaskLater(
            Pixelay.plugin(),
            () -> {
                mount();
                if (onMounted != null) {
                    onMounted.run();
                }
            },
            1
        );
    }

    /** Returns the world location of the player vehicle. */
    Location seatLocation() {
        return view.clone().add(0, seatHeight, 0);
    }

    /** Spawns an invisible marker armor stand with no hitbox. */
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

    /** Updates player attachment to the camera each server tick. */
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
        // Resend packets while the client resets tracking after game mode changes.
        if (ticks++ < RESEND_TICKS) {
            sendClickTarget();
            PacketUtils.mount(player, seat);
            PacketUtils.sendCamera(player, anchor);
        }
    }

    /** Restores camera view when spectator click packets displace client view. */
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

    /**
     * Removes the camera.
     *
     * @param restoreView True to restore player view, or false if another screen takes priority.
     */
    void remove(boolean restoreView) {
        removed = true;
        if (player.isOnline()) {
            PacketUtils.destroy(player, clickTargetId);
            if (restoreView) {
                PacketUtils.sendCamera(player, player);
            }
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
        // Mount passenger on server entity so vehicle look packets are accepted.
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
     * Spawns a packet-only interaction entity around the camera to capture spectator click events.
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
