package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.content.FontMetrics;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.VAlign;
import dev.horuse.pixelay.debug.LayoutDebug;
import dev.horuse.pixelay.utils.PacketUtils;
import org.bukkit.entity.Player;

/**
 * Per-player canvas cursor. A client-side Text Display glyph tracks the point where the player looks.
 * {@link #move} updates the position each tick.
 * The cursor provides aiming reference when the spectator camera hides the vanilla crosshair.
 * The component spawns through the fake packet renderer.
 */
public class CanvasCursor extends AbstractCanvasComponent {

    private static final double DEBUG_DOT_PX = 3;
    private static final int DEBUG_DOT_COLOR = 0xD0FF00FF;
    private final Player viewer;
    private final String glyph;
    /** Packet entity ID of the debug dot, or 0 when hidden. */
    private int debugDotId;
    /** Hotspot coordinates in font pixels from the top-left, or {@code null} to center the glyph. */
    private final double[] hotspot;
    /** Typed renderer reference for {@link FakeRenderer#move}. */
    private final FakeRenderer fakeRenderer;

    /** Creates a cursor with glyph and size settings from config.yml centered on the target pixel. */
    public CanvasCursor(Player viewer) {
        this(viewer, Pixelay.settings().cursor().glyph(), Pixelay.settings().cursor().heightPx());
    }

    /** Creates a cursor with specified glyph and height centered on the target pixel. */
    public CanvasCursor(Player viewer, String glyph, double heightPx) {
        this(viewer, glyph, heightPx, null);
    }

    /**
     * Creates a cursor with an offset hotspot that aligns with the target pixel.
     *
     * @param hotspotX font pixels from the left of the glyph
     * @param hotspotY font pixels from the top of the line box (negative when the glyph extends above)
     */
    public CanvasCursor(Player viewer, String glyph, double heightPx, double hotspotX, double hotspotY) {
        this(viewer, glyph, heightPx, new double[] { hotspotX, hotspotY });
    }

    private CanvasCursor(Player viewer, String glyph, double heightPx, double[] hotspot) {
        super(0, 0, heightPx);
        this.viewer = viewer;
        this.glyph = glyph;
        this.hotspot = hotspot;
        this.fakeRenderer = new FakeRenderer(viewer, this);
        this.renderer = fakeRenderer;
    }

    /** Moves the cursor hotspot to target canvas coordinates and spawns the entity if needed. */
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

        // Shift the cursor toward the viewer to prevent z-fighting with canvas text.
        float depth = Layers.cursor(canvas, viewer);
        TextPlacement.Placement placement = TextPlacement.place(this, glyph);
        fakeRenderer.move(canvas.pixelToWorld(placement.x(), placement.y(), depth).toVector());
        updateDebugDot(canvas, pixelX, pixelY, Layers.cursorDot(canvas, viewer));
    }

    /** Marks the pointed pixel with a debug dot in layout debug mode. */
    private void updateDebugDot(Canvas canvas, double pixelX, double pixelY, float depth) {
        if (!LayoutDebug.isEnabled(viewer)) {
            removeDebugDot();
            return;
        }
        double x = pixelX - DEBUG_DOT_PX / 2;
        double y = pixelY - DEBUG_DOT_PX / 2;
        if (debugDotId == 0) {
            debugDotId = ColorPanel.spawn(viewer, canvas, x, y, DEBUG_DOT_PX, DEBUG_DOT_PX, DEBUG_DOT_COLOR, depth);
        } else {
            ColorPanel.move(viewer, debugDotId, canvas, x, y, DEBUG_DOT_PX, DEBUG_DOT_PX, depth);
        }
    }

    private void removeDebugDot() {
        if (debugDotId != 0) {
            PacketUtils.destroy(viewer, debugDotId);
            debugDotId = 0;
        }
    }

    /**
     * Hides the cursor glyph when the player looks away. {@link #move} respawns the glyph when the player looks back.
     */
    public void hide() {
        despawn();
    }

    @Override
    public void despawn() {
        removeDebugDot();
        super.despawn();
    }

    /** No-op refresh. {@link #move} controls cursor positioning. */
    @Override
    public void refresh() {}

    /** Returns null clip to draw the full cursor across canvas boundaries. */
    @Override
    public Rect clip() {
        return null;
    }

    /** Disables background debug rendering because the glyph height exceeds standard lines. */
    @Override
    public boolean debugBackground() {
        return false;
    }

    @Override
    public String renderText() {
        return glyph;
    }
}
