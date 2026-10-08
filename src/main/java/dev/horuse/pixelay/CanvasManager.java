package dev.horuse.pixelay;

import dev.horuse.pixelay.config.PixelaySettings;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Central registry that ticks refresh and self-healing for every live {@link CanvasSurface}. */
public class CanvasManager {

    private final Set<CanvasSurface> surfaces = ConcurrentHashMap.newKeySet();

    private BukkitTask refreshTask;
    private BukkitTask healTask;

    public void start(JavaPlugin plugin) {
        PixelaySettings settings = Pixelay.settings();
        long refreshPeriod = settings.refreshPeriodTicks();
        long healPeriod = settings.healPeriodTicks();
        refreshTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            () -> {
                for (CanvasSurface surface : surfaces) {
                    surface.refresh();
                }
            },
            refreshPeriod,
            refreshPeriod
        );

        healTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            () -> {
                for (CanvasSurface surface : surfaces) {
                    if (!surface.isValid()) {
                        surface.respawn();
                    }
                }
            },
            healPeriod,
            healPeriod
        );
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }

        if (healTask != null) {
            healTask.cancel();
            healTask = null;
        }
    }

    public void register(CanvasSurface surface) {
        surfaces.add(surface);
    }

    public void unregister(CanvasSurface surface) {
        surfaces.remove(surface);
    }
}
