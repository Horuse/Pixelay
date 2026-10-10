package dev.horuse.pixelay.data;

import dev.horuse.pixelay.Pixelay;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

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
    /** Highest step used on each canvas, so the cursor sits right above it (and not far in front: parallax). */
    private static final Map<Canvas, Integer> TOP_STEPS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Step used by {@code /pixelay debug layers}: layers pulled far apart to see them like 3D devtools. */
    public static final float EXPLODED_STEP = 0.1f;

    private static volatile boolean exploded;
    private static volatile int revision;

    private Layers() {}

    /** Depth of a step, in blocks. */
    public static float at(double step) {
        return (float) (step * (exploded ? EXPLODED_STEP : Pixelay.settings().layers().step()));
    }

    /** @return whether layers are now pulled apart (for everyone: depths are shared by all viewers) */
    public static boolean toggleExploded() {
        exploded = !exploded;
        revision++;
        return exploded;
    }

    public static boolean isExploded() {
        return exploded;
    }

    /** Bumped whenever every depth changes, so renderers move what they already drew. */
    public static int revision() {
        return revision;
    }

    /** The text layer of a component outside a layout: the plane clicks are measured on. */
    public static float text() {
        return at(DEFAULT_TEXT_BACKGROUND_STEP + 1);
    }

    /** Called by layouts after each pass with the highest step they use on the canvas. */
    public static void reportTopStep(Canvas canvas, int step) {
        TOP_STEPS.merge(canvas, step, Math::max);
    }

    /**
     * One step above everything on the canvas. The cursor is seen at an angle, so it must stay as close to
     * the drawing as possible: the farther it is in front of it, the more it drifts off the pointed pixel.
     */
    public static float cursor(Canvas canvas) {
        int top = Math.max(TOP_STEPS.getOrDefault(canvas, 0), DEFAULT_TEXT_BACKGROUND_STEP + 1);
        return at(top + 1);
    }

    public static float cursorDot(Canvas canvas) {
        return cursor(canvas) + at(1);
    }
}
