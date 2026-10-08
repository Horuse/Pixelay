package dev.horuse.pixelay;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import dev.horuse.pixelay.commands.PixelayCommand;
import dev.horuse.pixelay.components.CanvasButton;
import dev.horuse.pixelay.components.CanvasClickCatcher;
import dev.horuse.pixelay.config.PixelaySettings;
import dev.horuse.pixelay.content.FontMetrics;
import dev.horuse.pixelay.listeners.CanvasClickListener;
import dev.horuse.pixelay.listeners.CanvasPacketClickListener;
import dev.horuse.pixelay.listeners.ChatSuppression;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurateException;

/**
 * Pixelay: draws pixel-positioned text, buttons and cursors on flat canvases made of Display
 * entities. Server-wide, per-player (visibility) and fully fake (packet-only) rendering are
 * supported; other plugins use it through {@link Pixelay}.
 */
public final class PixelayPlugin extends JavaPlugin {

    private final CanvasManager manager = new CanvasManager();
    private NamespacedKey entityKey;
    private PixelaySettings settings;
    private PacketListenerCommon packetClickListener;
    private PacketListenerCommon chatSuppressionListener;

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

        CanvasButton.clearRegistry();
        CanvasClickCatcher.clearRegistry();

        Bukkit.getPluginManager().registerEvents(new CanvasClickListener(), this);
        packetClickListener = PacketEvents.getAPI()
            .getEventManager()
            .registerListener(new CanvasPacketClickListener(), PacketListenerPriority.NORMAL);
        chatSuppressionListener = PacketEvents.getAPI()
            .getEventManager()
            .registerListener(new ChatSuppression(), PacketListenerPriority.NORMAL);

        PixelayCommand command = new PixelayCommand(this);
        getCommand("pixelay").setExecutor(command);
        getCommand("pixelay").setTabCompleter(command);

        manager.start(this);
    }

    @Override
    public void onDisable() {
        manager.stop();
        if (packetClickListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetClickListener);
            packetClickListener = null;
        }

        if (chatSuppressionListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(chatSuppressionListener);
            chatSuppressionListener = null;
        }

        CanvasButton.clearRegistry();
        CanvasClickCatcher.clearRegistry();
        if (entityKey != null) {
            cleanupCanvasEntities();
        }
    }

    /** Re-reads config.yml and font-widths.yml and restarts the scheduler with the new periods. */
    public void reload() throws ConfigurateException {
        loadSettings();
        manager.stop();
        manager.start(this);
    }

    private void loadSettings() throws ConfigurateException {
        settings = PixelaySettings.load(this);
        FontMetrics.loadOverrides(this);
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

    /** Removes any leftover canvas entities (e.g. after a crash or reload). */
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
