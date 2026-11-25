package dev.horuse.pixelay.hud;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the exact (anchor, y) font pairs actually used by HUD plugins at runtime
 * so {@link YFontGenerator} only emits required font definitions into the resource pack.
 */
public final class YFontRegistry {

    /** A single registered HUD font coordinate pair. */
    public record Entry(HudAnchor anchor, int y) {}

    private static final Set<Entry> ENTRIES = ConcurrentHashMap.newKeySet();

    private YFontRegistry() {}

    /** Registers a single (anchor, y) font offset pair. */
    public static void register(HudAnchor anchor, int y) {
        HudAnchor resolved = anchor != null ? anchor : HudAnchor.TOP_LEFT;
        if (resolved == HudAnchor.COMPASS_TAPE) {
            return;
        }
        ENTRIES.add(new Entry(resolved, y));
    }

    /** Registers {@code lineCount} consecutive 10px lines starting at {@code baseY}. */
    public static void registerLines(HudAnchor anchor, int baseY, int lineCount) {
        int count = Math.max(1, lineCount);
        for (int i = 0; i < count; i++) {
            register(anchor, baseY + i * 10);
        }
    }

    /** Returns an unmodifiable snapshot of all registered pairs. */
    public static Set<Entry> entries() {
        return Collections.unmodifiableSet(Set.copyOf(ENTRIES));
    }

    /** Returns true when at least one pair has been registered. */
    public static boolean hasRegistrations() {
        return !ENTRIES.isEmpty();
    }

    /** Clears all registered pairs. */
    public static void clear() {
        ENTRIES.clear();
    }
}
