package dev.horuse.pixelay;

import dev.horuse.pixelay.config.PixelaySettings;
import dev.horuse.pixelay.screen.Transitions;
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

    /** Fixed settings for offline use. The value is null when a server runs Pixelay. */
    private static PixelaySettings headlessSettings;

    private Pixelay() {}

    static void bind(PixelayPlugin instance) {
        plugin = instance;
    }

    /**
     * @return the running plugin instance. This method throws an exception if Pixelay is disabled.
     */
    public static JavaPlugin plugin() {
        return instance();
    }

    private static PixelayPlugin instance() {
        if (plugin == null) {
            throw new IllegalStateException("Pixelay is not enabled (add 'depend: [Pixelay]' to your plugin.yml)");
        }
        return plugin;
    }

    /**
     * @return the registry that ticks refresh and self-healing of registered surfaces
     */
    public static CanvasManager manager() {
        return instance().manager();
    }

    /**
     * @return the transition service for full-screen transitions outside canvas sessions
     */
    public static Transitions transitions() {
        return instance().transitions();
    }

    /**
     * Creates a screen HUD controller for the specified player.
     *
     * @param player the target player
     * @return a new screen HUD instance
     */
    public static dev.horuse.pixelay.hud.ScreenHud hud(org.bukkit.entity.Player player) {
        return new dev.horuse.pixelay.hud.ScreenHud(player);
    }

    /**
     * @return the current configuration snapshot (replaced on {@code /pixelay reload})
     */
    public static PixelaySettings settings() {
        if (headlessSettings != null) {
            return headlessSettings;
        }
        return instance().settings();
    }

    /**
     * Sets a fixed settings snapshot for offline use without a running server.
     * Layout previews use this method. Pass {@code null} to remove the snapshot.
     */
    public static void bindHeadlessSettings(PixelaySettings settings) {
        headlessSettings = settings;
    }

    /** @return true if a fixed settings snapshot is bound for offline use */
    public static boolean hasHeadlessSettings() {
        return headlessSettings != null;
    }

    /**
     * @return the PDC key that marks every entity spawned by Pixelay
     */
    public static NamespacedKey entityKey() {
        return instance().entityKey();
    }

    /** Logs a message only when {@code debug: true} is set in config.yml. */
    public static void debug(String message) {
        if (plugin != null && plugin.settings().debug()) {
            plugin.getLogger().info("[debug] " + message);
        }
    }
}
