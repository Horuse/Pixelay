package dev.horuse.pixelay.debug;

import dev.horuse.pixelay.utils.PlayerSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Per-player layout debug mode ({@code /pixelay debug layout}): every component gets a translucent
 * text background — blue when it fits, red where an overflow starts, orange inside an overflowing box.
 * The highlight follows the next canvas refresh (at most a second for managed surfaces).
 */
public final class LayoutDebug {

    /** Background of a component that fits its space, while debugging (ARGB). */
    public static final int DEBUG_BACKGROUND = 0x4000A0FF;

    /** Background of a component that is itself bigger than its space, while debugging (ARGB). */
    public static final int OVERFLOW_BACKGROUND = 0x60FF0000;

    /** Background of a component inside an overflowing box, while debugging (ARGB). */
    public static final int INSIDE_OVERFLOW_BACKGROUND = 0x60FF9900;

    private static final Set<UUID> VIEWERS = ConcurrentHashMap.newKeySet();

    private LayoutDebug() {}

    /** @return whether the mode is now enabled for the player */
    public static boolean toggle(Player player) {
        return PlayerSet.toggle(VIEWERS, player.getUniqueId());
    }

    /** Online players with the debug mode on. */
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

    /** Server-side displays are shared, so they show the highlight while anyone debugs. */
    public static boolean anyEnabled() {
        return !VIEWERS.isEmpty();
    }

    /** Forgets a player who left, so server-wide highlights do not stay on forever. */
    public static void remove(UUID playerId) {
        VIEWERS.remove(playerId);
    }

    public static void clear() {
        VIEWERS.clear();
    }

    /** The background a component should have for a viewer who does ({@code visible}) or does not debug. */
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
