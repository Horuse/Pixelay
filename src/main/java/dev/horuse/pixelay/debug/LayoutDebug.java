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
 * Per-player layout debug mode ({@code /pixelay debug layout}): draws wireframe box outlines
 * and shows a live DevTools inspector in the ActionBar when hovering over elements.
 */
public final class LayoutDebug {

    /** Background of a component that fits its space, while debugging (ARGB). */
    public static final int DEBUG_BACKGROUND = 0xFF1E3A8A;

    /** Background of a component that is itself bigger than its space, while debugging (ARGB). */
    public static final int OVERFLOW_BACKGROUND = 0xFF991B1B;

    /** Background of a component inside an overflowing box, while debugging (ARGB). */
    public static final int INSIDE_OVERFLOW_BACKGROUND = 0xFF9A3412;

    private static final Set<UUID> VIEWERS = ConcurrentHashMap.newKeySet();

    private LayoutDebug() {}

    /** Sends a live DevTools-style DOM inspector line to the debugging player's ActionBar. */
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
