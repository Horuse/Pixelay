package dev.horuse.pixelay;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import dev.horuse.pixelay.clicks.CanvasClickCatcher;
import dev.horuse.pixelay.clicks.CanvasClicks;
import dev.horuse.pixelay.commands.PixelayCommand;
import dev.horuse.pixelay.config.PixelaySettings;
import dev.horuse.pixelay.content.FontMetrics;
import dev.horuse.pixelay.debug.CanvasBoundsDebug;
import dev.horuse.pixelay.debug.LayoutDebug;
import dev.horuse.pixelay.listeners.CanvasPacketClickListener;
import dev.horuse.pixelay.listeners.ChatSuppression;
import dev.horuse.pixelay.listeners.PlayerQuitCleanup;
import dev.horuse.pixelay.pack.PixelayPack;
import dev.horuse.pixelay.screen.ScreenListener;
import dev.horuse.pixelay.screen.ScreenLookListener;
import dev.horuse.pixelay.screen.ScreenSession;
import dev.horuse.pixelay.screen.ShaderCursorListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurateException;

/**
 * Pixelay draws pixel-positioned text, buttons, and cursors on flat canvases with Display entities.
 * The plugin supports server-wide, per-player, and packet-only rendering modes. Other plugins
 * access the API through {@link Pixelay}.
 */
public final class PixelayPlugin extends JavaPlugin {

    private final CanvasManager manager = new CanvasManager();
    private NamespacedKey entityKey;
    private PixelaySettings settings;
    private final List<PacketListenerCommon> packetListeners = new ArrayList<>();

    @Override
    public void onEnable() {
        entityKey = new NamespacedKey(this, "canvas");
        Pixelay.bind(this);
        try {
            loadSettings();
        } catch (ConfigurateException e) {
            getLogger().severe("Failed to load configuration, disabling: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (settings.cleanupOnEnable()) {
            cleanupCanvasEntities();
        }

        CanvasClicks.clear();
        CanvasClickCatcher.clearRegistry();

        Bukkit.getPluginManager().registerEvents(new PlayerQuitCleanup(), this);
        Bukkit.getPluginManager().registerEvents(new ScreenListener(), this);
        for (PacketListener listener : List.of(
            new ScreenLookListener(),
            new ShaderCursorListener(),
            new CanvasPacketClickListener(),
            new ChatSuppression()
        )) {
            packetListeners.add(
                PacketEvents.getAPI().getEventManager().registerListener(listener, PacketListenerPriority.NORMAL)
            );
        }

        PixelayCommand command = new PixelayCommand(this);
        getCommand("pixelay").setExecutor(command);
        getCommand("pixelay").setTabCompleter(command);

        manager.start(this);
        CanvasBoundsDebug.start(this);
    }

    @Override
    public void onDisable() {
        // Restore player game mode and position before closing sessions.
        ScreenSession.closeAll();
        manager.stop();
        CanvasBoundsDebug.stop();
        packetListeners.forEach(PacketEvents.getAPI().getEventManager()::unregisterListener);
        packetListeners.clear();

        CanvasClicks.clear();
        CanvasClickCatcher.clearRegistry();
        LayoutDebug.clear();
        if (entityKey != null) {
            cleanupCanvasEntities();
        }
    }

    /** Re-reads config.yml and font-widths.yml, then restarts the scheduler with the new intervals. */
    public void reload() throws ConfigurateException {
        loadSettings();
        manager.stop();
        manager.start(this);
    }

    private void loadSettings() throws ConfigurateException {
        settings = PixelaySettings.load(this);
        FontMetrics.loadOverrides(this);
        buildResourcePack();
    }

    /** Builds plugins/Pixelay/pixelay-pack.zip. A build failure disables shader features only. */
    private void buildResourcePack() {
        if (!settings.resourcePack().enabled()) {
            return;
        }
        try {
            PixelayPack.Built pack = PixelayPack.build(
                getFile(),
                getDataFolder().toPath(),
                settings.resourcePack(),
                getLogger()
            );
            getLogger().info("Resource pack built: " + pack.zip() + " (sha1 " + pack.sha1() + ")");
        } catch (IOException e) {
            getLogger().warning("Could not build the resource pack: " + e.getMessage());
        }
    }

    public CanvasManager manager() {
        return manager;
    }

    public PixelaySettings settings() {
        return settings;
    }

    public NamespacedKey entityKey() {
        return entityKey;
    }

    /** Removes leftover canvas entities after a server crash or plugin reload. */
    private void cleanupCanvasEntities() {
        for (World world : Bukkit.getWorlds()) {
            world
                .getEntities()
                .stream()
                .filter(entity -> entity.getPersistentDataContainer().has(entityKey, PersistentDataType.BYTE))
                .forEach(Entity::remove);
        }
    }
}
