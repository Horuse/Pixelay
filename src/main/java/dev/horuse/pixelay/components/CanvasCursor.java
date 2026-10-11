package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.content.FontMetrics;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.data.VAlign;
import dev.horuse.pixelay.debug.DebugPanel;
import dev.horuse.pixelay.debug.LayoutDebug;
import org.bukkit.entity.Player;

/**
 * A per-player canvas cursor: a single client-side Text Display glyph that follows the point the
 * player is looking at (moved each tick via {@link #move}). Used where the vanilla crosshair is
 * hidden (spectator camera), so the player still sees where they point. Spawned through the shared
 * fake packet renderer like any other component.
 */
public class CanvasCursor extends AbstractCanvasComponent {

    private static final double DEBUG_DOT_PX = 3;
    private static final int DEBUG_DOT_COLOR = 0xD0FF00FF;
    private final Player viewer;
    private final String glyph;
    /** Fake entity id of the debug hot-spot dot, 0 when not shown. */
    private int debugDotId;
    /** Hot spot in font px from the glyph's line-box top-left, or {@code null} to centre the glyph. */
    private final double[] hotspot;

    /** Cursor with the glyph and size from config.yml ({@code cursor.*}), centred on the pointed pixel. */
    public CanvasCursor(Player viewer) {
        this(viewer, Pixelay.settings().cursor().glyph(), Pixelay.settings().cursor().heightPx());
    }

    /** Cursor with an explicit MiniMessage glyph and pixel height, centred on the pointed pixel. */
    public CanvasCursor(Player viewer, String glyph, double heightPx) {
        this(viewer, glyph, heightPx, null);
    }

    /**
     * Cursor whose hot spot — e.g. the tip of an arrow — lands exactly on the pointed pixel.
     *
     * @param hotspotX font px from the left of the glyph
     * @param hotspotY font px from the top of the line box (negative when the glyph rises above it)
     */
    public CanvasCursor(Player viewer, String glyph, double heightPx, double hotspotX, double hotspotY) {
        this(viewer, glyph, heightPx, new double[] { hotspotX, hotspotY });
    }

    private CanvasCursor(Player viewer, String glyph, double heightPx, double[] hotspot) {
        super(0, 0, heightPx);
        this.viewer = viewer;
        this.glyph = glyph;
        this.hotspot = hotspot;
        this.renderer = new FakePacketCanvasRenderer(viewer, this);
    }

    /** Repositions the cursor so its hot spot is on the given canvas pixel (spawning it first if needed). */
    public void move(Canvas canvas, double pixelX, double pixelY) {
        if (hotspot == null) {
            place(pixelX, pixelY, HAlign.CENTER, VAlign.MIDDLE);
        } else {
            double fontPx = heightPx / FontMetrics.LINE_HEIGHT_PX;
            place(pixelX - hotspot[0] * fontPx, pixelY - hotspot[1] * fontPx, HAlign.LEFT, VAlign.TOP);
        }
        if (!isValid()) {
            spawn(canvas);
        }

        // Lifted towards the viewer: text on the same plane would otherwise overlap the cursor at random.
        float depth = Layers.cursor(canvas, viewer);
        double[] position = CanvasLayout.displayPosition(this, glyph);
        ((FakePacketCanvasRenderer) renderer).move(canvas.pixelToWorld(position[0], position[1], depth).toVector());
        updateDebugDot(canvas, pixelX, pixelY, Layers.cursorDot(canvas, viewer));
    }

    /** In layout debug mode, marks the exact pointed pixel (the hot spot) with a small dot. */
    private void updateDebugDot(Canvas canvas, double pixelX, double pixelY, float depth) {
        if (!LayoutDebug.isEnabled(viewer)) {
            removeDebugDot();
            return;
        }
        double x = pixelX - DEBUG_DOT_PX / 2;
        double y = pixelY - DEBUG_DOT_PX / 2;
        if (debugDotId == 0) {
            debugDotId = DebugPanel.spawn(viewer, canvas, x, y, DEBUG_DOT_PX, DEBUG_DOT_PX, DEBUG_DOT_COLOR, depth);
        } else {
            DebugPanel.move(viewer, debugDotId, canvas, x, y, DEBUG_DOT_PX, DEBUG_DOT_PX, depth);
        }
    }

    private void removeDebugDot() {
        if (debugDotId != 0) {
            DebugPanel.destroy(viewer, debugDotId);
            debugDotId = 0;
        }
    }

    /**
     * Hides the cursor glyph (e.g. when the player is not looking at the canvas); {@link #move}
     * respawns it the next time the player looks back.
     */
    public void hide() {
        despawn();
    }

    @Override
    public void despawn() {
        removeDebugDot();
        super.despawn();
    }

    /** The cursor is positioned only by {@link #move}: a refresh must not move it back onto the text plane. */
    @Override
    public void refresh() {}

    /** The glyph's text box is not the drawing (the arrow is taller than a line): debug shows a dot instead. */
    @Override
    public boolean debugBackground() {
        return false;
    }

    @Override
    public String renderText() {
        return glyph;
    }
}
