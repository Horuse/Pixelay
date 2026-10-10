package dev.horuse.pixelay.debug;

import dev.horuse.pixelay.data.Canvas;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Canvas outlines ({@code /pixelay debug bounds}): every canvas with spawned components near a debugging
 * player is outlined with particles on the plane its components are drawn on, with a cross in its
 * centre — to check that a canvas sits where its background (model, map, wall) is.
 */
public final class CanvasBoundsDebug {

    private static final double VIEW_DISTANCE = 48;
    private static final long PERIOD_TICKS = 5;
    private static final double STEP_BLOCKS = 0.1;
    private static final Particle.DustOptions DUST = new Particle.DustOptions(Color.fromRGB(0, 255, 255), 0.35f);

    /** Spawned components per canvas; canvases nobody holds any more disappear by themselves. */
    private static final Map<Canvas, Integer> CANVASES = new WeakHashMap<>();
    private static final Set<UUID> VIEWERS = ConcurrentHashMap.newKeySet();
    private static BukkitTask task;

    private CanvasBoundsDebug() {}

    public static synchronized void track(Canvas canvas) {
        CANVASES.merge(canvas, 1, Integer::sum);
    }

    public static synchronized void untrack(Canvas canvas) {
        CANVASES.computeIfPresent(canvas, (key, count) -> count > 1 ? count - 1 : null);
    }

    /** @return whether the outlines are now shown to the player */
    public static boolean toggle(Player player) {
        if (VIEWERS.remove(player.getUniqueId())) {
            return false;
        }
        VIEWERS.add(player.getUniqueId());
        return true;
    }

    public static void remove(UUID playerId) {
        VIEWERS.remove(playerId);
    }

    public static void start(JavaPlugin plugin) {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, CanvasBoundsDebug::draw, PERIOD_TICKS, PERIOD_TICKS);
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        VIEWERS.clear();
    }

    private static void draw() {
        if (VIEWERS.isEmpty()) {
            return;
        }
        List<Canvas> canvases;
        synchronized (CanvasBoundsDebug.class) {
            canvases = new ArrayList<>(CANVASES.keySet());
        }
        for (UUID id : VIEWERS) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer == null) {
                continue;
            }
            for (Canvas canvas : canvases) {
                Location center = canvas.center();
                if (
                    viewer.getWorld().equals(center.getWorld()) &&
                    viewer.getLocation().distanceSquared(center) <= VIEW_DISTANCE * VIEW_DISTANCE
                ) {
                    outline(viewer, canvas);
                }
            }
        }
    }

    private static void outline(Player viewer, Canvas canvas) {
        double w = canvas.resolution().pixelWidth();
        double h = canvas.resolution().pixelHeight();
        Location tl = canvas.pixelToWorld(0, 0);
        Location tr = canvas.pixelToWorld(w, 0);
        Location bl = canvas.pixelToWorld(0, h);
        Location br = canvas.pixelToWorld(w, h);
        line(viewer, tl, tr);
        line(viewer, tr, br);
        line(viewer, br, bl);
        line(viewer, bl, tl);
        double arm = Math.min(w, h) * 0.05;
        line(viewer, canvas.pixelToWorld(w / 2 - arm, h / 2), canvas.pixelToWorld(w / 2 + arm, h / 2));
        line(viewer, canvas.pixelToWorld(w / 2, h / 2 - arm), canvas.pixelToWorld(w / 2, h / 2 + arm));
    }

    private static void line(Player viewer, Location a, Location b) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double dz = b.getZ() - a.getZ();
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / STEP_BLOCKS));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            viewer.spawnParticle(
                Particle.DUST,
                a.getX() + dx * t,
                a.getY() + dy * t,
                a.getZ() + dz * t,
                1,
                0,
                0,
                0,
                0,
                DUST,
                true
            );
        }
    }
}
