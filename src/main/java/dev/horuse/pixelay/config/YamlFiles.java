package dev.horuse.pixelay.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Loads YAML files from the plugin data directory with Configurate and copies bundled defaults when missing.
 */
public final class YamlFiles {

    private YamlFiles() {}

    /** Copies bundled defaults to the data directory if missing and parses the YAML configuration. */
    public static CommentedConfigurationNode load(JavaPlugin plugin, String name) throws ConfigurateException {
        Path folder = plugin.getDataFolder().toPath();
        Path file = folder.resolve(name);

        if (!Files.exists(file)) {
            try (InputStream defaults = plugin.getResource(name)) {
                if (defaults != null) {
                    Files.createDirectories(folder);
                    Files.copy(defaults, file);
                }
            } catch (IOException e) {
                throw new ConfigurateException("Failed to write default " + name, e);
            }
        }

        return YamlConfigurationLoader.builder().path(file).build().load();
    }
}
