package dev.horuse.pixelay.data;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.utils.PlayerSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Controls layer depth offsets for rendered elements in front of the canvas surface.
 * Elements receive depth steps according to painting order.
 * Overlapping boxes receive higher steps to prevent rendering conflicts.
 * Standalone components use default base steps.
 */
public final class Layers {

    /** Maximum layer step permitted for a layout. */
    public static final int MAX_STEP = 60;
    /** Layer step for background plates of standalone components. */
    public static final int DEFAULT_TEXT_BACKGROUND_STEP = 1;
    /** Layer step for text of standalone components. */
    public static final int DEFAULT_TEXT_STEP = DEFAULT_TEXT_BACKGROUND_STEP + 1;
    /** Layer step used by layer inspection debug mode. */
    public static final float EXPLODED_STEP = 0.1f;
    /** Tracks the maximum layer step per canvas to position the cursor. */
    private static final Map<Canvas, Map<Object, Integer>> TOP_STEPS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Tracks players who currently view exploded layers. */
    private static final Set<UUID> EXPLODED = ConcurrentHashMap.newKeySet();

    private Layers() {}

    /** Calculates layer depth in blocks for a step. */
    public static float at(double step) {
        return at(step, null);
    }

    /** Calculates layer depth in blocks for a step and viewer. */
    public static float at(double step, Player viewer) {
        boolean pulledApart = viewer != null && EXPLODED.contains(viewer.getUniqueId());
        return (float) (step * (pulledApart ? EXPLODED_STEP : Pixelay.settings().layers().step()));
    }

    /** @return true if exploded mode is now enabled for the player */
    public static boolean toggleExploded(Player player) {
        // Renderers apply new depths on the next refresh.
        return PlayerSet.toggle(EXPLODED, player.getUniqueId());
    }

    public static void forget(UUID player) {
        EXPLODED.remove(player);
    }

    /** Returns the standard text layer depth for click detection. */
    public static float text() {
        return at(DEFAULT_TEXT_STEP);
    }

    /** Updates the highest active step for a layout on a canvas. */
    public static void reportTopStep(Canvas canvas, Object layout, int step) {
        synchronized (TOP_STEPS) {
            TOP_STEPS.computeIfAbsent(canvas, key -> new IdentityHashMap<>()).put(layout, step);
        }
    }

    /** Removes tracked step data when a layout unmounts. */
    public static void forgetTopStep(Canvas canvas, Object layout) {
        synchronized (TOP_STEPS) {
            Map<Object, Integer> layouts = TOP_STEPS.get(canvas);
            if (layouts != null) {
                layouts.remove(layout);
            }
        }
    }

    /** Calculates cursor depth one step above the highest canvas element. */
    public static float cursor(Canvas canvas, Player viewer) {
        int top = DEFAULT_TEXT_STEP;
        synchronized (TOP_STEPS) {
            Map<Object, Integer> layouts = TOP_STEPS.get(canvas);
            if (layouts != null) {
                for (int step : layouts.values()) {
                    top = Math.max(top, step);
                }
            }
        }
        return at(top + 1, viewer);
    }

    public static float cursorDot(Canvas canvas, Player viewer) {
        return cursor(canvas, viewer) + at(1, viewer);
    }
}
