package dev.horuse.pixelay.debug;

import dev.horuse.pixelay.utils.PlayerSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Layout debug mode displaying wireframe outlines and inspector details in the action bar.
 */
public final class LayoutDebug {

    /** Background ARGB color for elements fitting their bounds during debugging. */
    public static final int DEBUG_BACKGROUND = 0xFF1E3A8A;

    /** Background ARGB color for elements exceeding their bounds during debugging. */
    public static final int OVERFLOW_BACKGROUND = 0xFF991B1B;

    /** Background ARGB color for elements inside overflowing ancestors during debugging. */
    public static final int INSIDE_OVERFLOW_BACKGROUND = 0xFF9A3412;

    private static final Set<UUID> VIEWERS = ConcurrentHashMap.newKeySet();

    private LayoutDebug() {}

    /** Sends element inspection data to the action bar of the debugging player. */
    public static void sendInspector(
        Player player,
        String tagName,
        String id,
        double x,
        double y,
        double w,
        double h,
        int step,
        int zIndex
    ) {
        if (!isEnabled(player)) {
            return;
        }
        String idPart = id != null && !id.isBlank() ? "<#FACC15>#" + id : "";
        String msg = String.format(
            java.util.Locale.ROOT,
            "<#38BDF8><b><%s%s></b> <#94A3B8>• <#E2E8F0>%dx%d px <#64748B>at (%d, %d) <#94A3B8>• <#A3E635>step %d <#64748B>(z:%d)",
            tagName,
            idPart,
            Math.round(w),
            Math.round(h),
            Math.round(x),
            Math.round(y),
            step,
            zIndex
        );
        player.sendActionBar(MiniMessage.miniMessage().deserialize(msg));
    }

    /** @return true if layout debug mode is now enabled for the player */
    public static boolean toggle(Player player) {
        return PlayerSet.toggle(VIEWERS, player.getUniqueId());
    }

    /** Returns all online players who have debug mode enabled. */
    public static List<Player> onlineViewers() {
        List<Player> players = new ArrayList<>();
        for (UUID id : VIEWERS) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    public static boolean isEnabled(Player player) {
        return player != null && VIEWERS.contains(player.getUniqueId());
    }

    /** Checks if any player has layout debug mode enabled. */
    public static boolean anyEnabled() {
        return !VIEWERS.isEmpty();
    }

    /** Removes a disconnected player from active viewers. */
    public static void remove(UUID playerId) {
        VIEWERS.remove(playerId);
    }

    public static void clear() {
        VIEWERS.clear();
    }

    /** Calculates debug background color based on element fit and viewer state. */
    public static int background(Fit fit, boolean visible, int normal) {
        if (!visible) {
            return normal;
        }
        return switch (fit) {
            case NONE -> DEBUG_BACKGROUND;
            case SELF -> OVERFLOW_BACKGROUND;
            case INSIDE -> INSIDE_OVERFLOW_BACKGROUND;
        };
    }
}
