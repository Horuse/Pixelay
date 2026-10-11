package dev.horuse.pixelay.data;

import dev.horuse.pixelay.Pixelay;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Depth of everything Pixelay draws, in blocks in front of the canvas surface (the plane moved out by the
 * canvas' {@code depthOffset}, i.e. the face of your background). Depths are whole {@code layers.step}s, so
 * two things on different steps never fight for depth, whatever the background is.
 * <p>
 * Layouts give their boxes steps in painting order, like CSS: a parent before its children, an earlier
 * sibling before a later one. A box goes one step above every box painted before it that it overlaps, so
 * nested boxes always lie above their parents and the stack only grows where things really overlap. A
 * container takes one step (its background), a component two (its text background and its text above it).
 * Debug panels sit half a step under the box they outline. A component outside a layout takes steps 1
 * and 2; the cursor is one step above the highest step of its canvas.
 */
public final class Layers {

    /** Highest step a layout may use; deeper overlap shares it (a warning is logged). */
    public static final int MAX_STEP = 60;
    /** Steps of a component outside a layout: its text background, then its text. */
    public static final int DEFAULT_TEXT_BACKGROUND_STEP = 1;
    /**
     * Highest step of every layout on each canvas, so the cursor sits right above the current top (and not
     * far in front: parallax). Canvases are compared by identity and forgotten when dropped.
     */
    private static final Map<Canvas, Map<Object, Integer>> TOP_STEPS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Step used by {@code /pixelay debug layers}: layers pulled far apart to see them like 3D devtools. */
    public static final float EXPLODED_STEP = 0.1f;

    /** Players who see the layers pulled apart. */
    private static final Set<UUID> EXPLODED = ConcurrentHashMap.newKeySet();
    private static volatile int revision;

    private Layers() {}

    /** Depth of a step, in blocks. */
    public static float at(double step) {
        return at(step, null);
    }

    /**
     * Depth of a step for what {@code viewer} alone sees (fake and per-player entities); {@code null} for
     * shared entities. Only per-viewer entities are pulled apart by {@code /pixelay debug layers}.
     */
    public static float at(double step, Player viewer) {
        boolean pulledApart = viewer != null && EXPLODED.contains(viewer.getUniqueId());
        return (float) (step * (pulledApart ? EXPLODED_STEP : Pixelay.settings().layers().step()));
    }

    /** @return whether {@code player} now sees the layers of their own (fake and per-player) canvases apart */
    public static boolean toggleExploded(Player player) {
        boolean on = EXPLODED.add(player.getUniqueId()) || !EXPLODED.remove(player.getUniqueId());
        revision++;
        return on;
    }

    public static void forget(UUID player) {
        if (EXPLODED.remove(player)) {
            revision++;
        }
    }

    /** Bumped whenever depths change for someone, so renderers move what they already drew. */
    public static int revision() {
        return revision;
    }

    /** The text layer of a component outside a layout: the plane clicks are measured on. */
    public static float text() {
        return at(DEFAULT_TEXT_BACKGROUND_STEP + 1);
    }

    /** Called by a layout after each pass with the highest step it uses on the canvas. */
    public static void reportTopStep(Canvas canvas, Object layout, int step) {
        synchronized (TOP_STEPS) {
            TOP_STEPS.computeIfAbsent(canvas, key -> new IdentityHashMap<>()).put(layout, step);
        }
    }

    /** Called by a layout when it leaves the canvas. */
    public static void forgetTopStep(Canvas canvas, Object layout) {
        synchronized (TOP_STEPS) {
            Map<Object, Integer> layouts = TOP_STEPS.get(canvas);
            if (layouts != null) {
                layouts.remove(layout);
            }
        }
    }

    /**
     * One step above everything on the canvas. The cursor is seen at an angle, so it must stay as close to
     * the drawing as possible: the farther it is in front of it, the more it drifts off the pointed pixel.
     */
    public static float cursor(Canvas canvas, Player viewer) {
        int top = DEFAULT_TEXT_BACKGROUND_STEP + 1;
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
