package dev.horuse.pixelay.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Resolves global placeholders without player context.
 * Server display entities are shared across viewers, so only global placeholders resolve.
 * Returns templates unchanged when PlaceholderAPI is missing.
 */
public final class PlaceholderResolver {

    private PlaceholderResolver() {}

    private static Boolean available;

    private static boolean isAvailable() {
        if (available == null) {
            available = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        }

        return available;
    }

    /**
     * Resolves global placeholders in the template string.
     *
     * @param template Raw text that can contain placeholders.
     * @return Resolved string, or template unchanged if PlaceholderAPI is unavailable.
     */
    public static String resolve(String template) {
        if (template == null || template.isEmpty() || !isAvailable()) {
            return template;
        }

        return PlaceholderAPI.setPlaceholders((OfflinePlayer) null, template);
    }
}
