package dev.horuse.pixelay.clicks;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.utils.ClickDebounce;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Unified click dispatcher for all canvases. A click transforms into canvas pixel coordinates
 * from the player look ray. Pixelay sends the pixel coordinates to registered {@link ClickTarget} instances,
 * newest first, until one handles the click. The source can be:
 * <ul>
 *   <li>World canvases with {@link Canvas#worldClicks(boolean)} via invisible {@link HitArea} tiles</li>
 *   <li>Full-screen screens at the cursor position</li>
 *   <li>External plugin code via {@link #click(Canvas, Player, double, double, Player)}</li>
 * </ul>
 * Register targets per canvas and viewer ({@code null} means all players). Call methods on the main thread only,
 * except {@link #onEntityClick}.
 */
public final class CanvasClicks {

    private static final Map<Key, HitArea> AREAS = new ConcurrentHashMap<>();
    private static final Map<Integer, HitArea> BY_ENTITY = new ConcurrentHashMap<>();
    private static final ClickDebounce DEBOUNCE = new ClickDebounce();

    private CanvasClicks() {}

    /** A canvas instance and an optional viewer UUID. Canvases compare by identity. */
    private record Key(Canvas canvas, UUID viewer) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.canvas == canvas && java.util.Objects.equals(k.viewer, viewer);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(canvas) * 31 + java.util.Objects.hashCode(viewer);
        }
    }

    /**
     * Registers {@code target} on {@code canvas} for {@code viewer} ({@code null} for all players).
     * Spawns hit tiles on first registration.
     */
    public static void register(Canvas canvas, Player viewer, ClickTarget target) {
        Key key = new Key(canvas, viewer != null ? viewer.getUniqueId() : null);
        HitArea area = AREAS.computeIfAbsent(key, k -> {
            HitArea created = new HitArea(canvas, viewer);
            created.spawn();
            return created;
        });
        area.targets.remove(target);
        area.targets.add(target);
    }

    /** Removes a click target. Despawns hit tiles when the target list becomes empty. */
    public static void unregister(Canvas canvas, Player viewer, ClickTarget target) {
        Key key = new Key(canvas, viewer != null ? viewer.getUniqueId() : null);
        HitArea area = AREAS.get(key);
        if (area == null) {
            return;
        }
        area.targets.remove(target);
        if (area.targets.isEmpty()) {
            AREAS.remove(key);
            area.despawn();
        }
    }

    /**
     * Routes a click at canvas coordinates to viewer targets, then to shared targets.
     *
     * @return true if a target handled the click, false otherwise
     */
    public static boolean click(Canvas canvas, Player viewer, double pixelX, double pixelY, Player player) {
        if (viewer != null && dispatch(AREAS.get(new Key(canvas, viewer.getUniqueId())), pixelX, pixelY, player)) {
            return true;
        }
        return dispatch(AREAS.get(new Key(canvas, null)), pixelX, pixelY, player);
    }

    /**
     * Processes an entity click from the packet listener.
     *
     * @return true if the entity is a hit tile, false otherwise
     */
    public static boolean onEntityClick(int entityId, Player player) {
        HitArea area = BY_ENTITY.get(entityId);
        if (area == null) {
            return false;
        }
        if (area.viewer != null && !area.viewer.getUniqueId().equals(player.getUniqueId())) {
            return true; // a modified client may click another player's private tiles
        }
        if (!DEBOUNCE.accept(player.getUniqueId())) {
            return true; // one right click arrives as two packets
        }
        Bukkit.getScheduler().runTask(Pixelay.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            Location eye = player.getEyeLocation();
            // Packet arrives before reach checks. Verify world and reach distance before dispatching.
            if (!eye.getWorld().equals(area.canvas.center().getWorld())) {
                return;
            }
            double[] pixel = area.canvas.rayHitInside(eye.toVector(), eye.getDirection());
            double reach = Pixelay.settings().clicks().maxDistance();
            if (pixel != null && area.canvas.pixelToWorld(pixel[0], pixel[1]).distanceSquared(eye) <= reach * reach) {
                Pixelay.debug(
                    "canvas click " + Math.round(pixel[0]) + "x" + Math.round(pixel[1]) + " by " + player.getName()
                );
                dispatch(area, pixel[0], pixel[1], player);
            }
        });
        return true;
    }

    /** Respawns destroyed server tiles during self-healing. */
    public static void heal() {
        for (HitArea area : AREAS.values()) {
            if (!area.isValid()) {
                area.despawn();
                area.spawn();
            }
        }
    }

    /** Respawns all click tiles when backing layers move. */
    public static void respawnAll() {
        for (HitArea area : AREAS.values()) {
            area.despawn();
            area.spawn();
        }
    }

    /** Clears all click areas and state on plugin disable. */
    public static void clear() {
        AREAS.values().forEach(HitArea::despawn);
        AREAS.clear();
        BY_ENTITY.clear();
        DEBOUNCE.clear();
    }

    /** Removes data for a disconnected player and despawns private tiles. */
    public static void remove(Player player) {
        DEBOUNCE.forget(player.getUniqueId());
        AREAS.entrySet().removeIf(entry -> {
            if (player.getUniqueId().equals(entry.getKey().viewer())) {
                entry.getValue().despawn();
                return true;
            }
            return false;
        });
    }

    private static boolean dispatch(HitArea area, double px, double py, Player player) {
        if (area == null) {
            return false;
        }
        List<ClickTarget> targets = new ArrayList<>(area.targets);
        for (int i = targets.size() - 1; i >= 0; i--) {
            if (targets.get(i).click(px, py, player)) {
                return true;
            }
        }
        return false;
    }

    static void bind(int entityId, HitArea area) {
        BY_ENTITY.put(entityId, area);
    }

    static void unbind(int entityId) {
        BY_ENTITY.remove(entityId);
    }
}
