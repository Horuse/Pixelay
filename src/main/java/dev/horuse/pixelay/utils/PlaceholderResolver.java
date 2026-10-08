package dev.horuse.pixelay.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Global, player-agnostic placeholder resolution.
 *
 * <p>Server-side display entities are shared across all viewers, so only global placeholders
 * (server stats, global variables, …) make sense here. Player-bound placeholders resolve to empty.
 * When PlaceholderAPI is absent the template is returned unchanged.
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
     * Resolves global placeholders in the template. No player context is applied.
     *
     * @param template raw text possibly containing {@code %...%} placeholders
     * @return the resolved text, or the template unchanged when PlaceholderAPI is not installed
     */
    public static String resolve(String template) {
        if (template == null || template.isEmpty() || !isAvailable()) {
            return template;
        }

        return PlaceholderAPI.setPlaceholders((OfflinePlayer) null, template);
    }
}
