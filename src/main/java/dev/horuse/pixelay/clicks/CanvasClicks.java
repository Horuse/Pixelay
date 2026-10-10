package dev.horuse.pixelay.clicks;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.data.Canvas;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * One click path for every canvas: a click becomes a canvas pixel (the clicking player's look ray hit on
 * the plane), and that pixel goes to the registered {@link ClickTarget}s, the latest first, until one
 * handles it. Where the click comes from does not matter:
 * <ul>
 *   <li>world canvases with {@link Canvas#worldClicks(boolean)} — invisible tiles over the plane catch
 *       the click (see {@link HitArea});</li>
 *   <li>full-screen screens — the cursor position;</li>
 *   <li>your own code — {@link #click(Canvas, Player, double, double, Player)}.</li>
 * </ul>
 * Targets are registered per canvas and viewer ({@code null} = everyone). Main thread only, except
 * {@link #onEntityClick}, which may be called from the network thread.
 */
public final class CanvasClicks {

    private static final Map<Key, HitArea> AREAS = new ConcurrentHashMap<>();
    private static final Map<Integer, HitArea> BY_ENTITY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();

    private CanvasClicks() {}

    /** A canvas object and a viewer (or nobody); canvases are compared by identity. */
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
     * Makes {@code target} clickable on {@code canvas} for {@code viewer} ({@code null} = everyone),
     * spawning the canvas' hit tiles on the first registration.
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

    /** Removes a target; the hit tiles go away with the last one. */
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
     * Routes a click at a known canvas pixel to the targets of {@code viewer} and then to the shared ones.
     *
     * @return whether a target handled it
     */
    public static boolean click(Canvas canvas, Player viewer, double pixelX, double pixelY, Player player) {
        if (viewer != null && dispatch(AREAS.get(new Key(canvas, viewer.getUniqueId())), pixelX, pixelY, player)) {
            return true;
        }
        return dispatch(AREAS.get(new Key(canvas, null)), pixelX, pixelY, player);
    }

    /**
     * Called by the packet listener for a click on any entity.
     *
     * @return whether the entity is a hit tile (the packet must then be cancelled)
     */
    public static boolean onEntityClick(int entityId, Player player) {
        HitArea area = BY_ENTITY.get(entityId);
        if (area == null) {
            return false;
        }
        if (area.viewer != null && !area.viewer.getUniqueId().equals(player.getUniqueId())) {
            return true; // a modified client may click another player's private tiles
        }
        long now = System.currentTimeMillis();
        Long last = LAST_CLICK.put(player.getUniqueId(), now);
        if (last != null && now - last < Pixelay.settings().clicks().debounceMs()) {
            return true; // one right click arrives as two packets
        }
        Bukkit.getScheduler().runTask(Pixelay.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            Location eye = player.getEyeLocation();
            // The packet is taken before the server checks the reach, so a modified client could send it
            // from anywhere: the clicked point must be in the player's world and within reach.
            if (!eye.getWorld().equals(area.canvas.center().getWorld())) {
                return;
            }
            double[] pixel = area.canvas.pixelFromRay(eye.toVector(), eye.getDirection());
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

    /** Respawns server tiles killed by something else (called by the self-heal task). */
    public static void heal() {
        for (HitArea area : AREAS.values()) {
            if (!area.isValid()) {
                area.despawn();
                area.spawn();
            }
        }
    }

    /** Drops everything (plugin disable). */
    public static void clear() {
        AREAS.values().forEach(HitArea::despawn);
        AREAS.clear();
        BY_ENTITY.clear();
        LAST_CLICK.clear();
    }

    /** Forgets a player who left: their private tiles are gone with their client. */
    public static void remove(Player player) {
        LAST_CLICK.remove(player.getUniqueId());
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
