package dev.horuse.pixelay;

import dev.horuse.pixelay.config.PixelaySettings;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Static entry point of the Pixelay API for other plugins.
 *
 * <pre>{@code
 * Canvas canvas = new Canvas(location, yaw, new CanvasResolution(256, 144, 4, 2.25));
 * CanvasText title = new CanvasText(128, 20, 16, TextContent.of("<gold>Hello"));
 * title.spawn(canvas);
 * Pixelay.manager().register(mySurface);
 * }</pre>
 */
public final class Pixelay {

    private static PixelayPlugin plugin;

    private Pixelay() {}

    static void bind(PixelayPlugin instance) {
        plugin = instance;
    }

    /**
     * @return the running plugin instance; throws if Pixelay is not enabled
     */
    public static JavaPlugin plugin() {
        if (plugin == null) {
            throw new IllegalStateException("Pixelay is not enabled (add 'depend: [Pixelay]' to your plugin.yml)");
        }

        return plugin;
    }

    /**
     * @return the registry that ticks refresh and self-healing of registered surfaces
     */
    public static CanvasManager manager() {
        return ((PixelayPlugin) plugin()).manager();
    }

    /**
     * @return the current configuration snapshot (replaced on {@code /pixelay reload})
     */
    public static PixelaySettings settings() {
        return ((PixelayPlugin) plugin()).settings();
    }

    /**
     * @return the PDC key that marks every entity spawned by Pixelay
     */
    public static NamespacedKey entityKey() {
        return ((PixelayPlugin) plugin()).entityKey();
    }

    /** Logs a message only when {@code debug: true} is set in config.yml. */
    public static void debug(String message) {
        if (plugin != null && plugin.settings().debug()) {
            plugin.getLogger().info("[debug] " + message);
        }
    }
}
