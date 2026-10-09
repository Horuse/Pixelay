package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.data.Canvas;
import org.bukkit.entity.Player;

/**
 * A per-player canvas cursor: a single client-side Text Display glyph that follows the point the
 * player is looking at (moved each tick via {@link #move}). Used where the vanilla crosshair is
 * hidden (spectator camera), so the player still sees where they point. Spawned through the shared
 * fake packet renderer like any other component.
 */
public class CanvasCursor extends AbstractCanvasComponent {

    private final String glyph;

    /** Cursor with the glyph and size from config.yml ({@code cursor.*}). */
    public CanvasCursor(Player viewer) {
        this(viewer, Pixelay.settings().cursor().glyph(), Pixelay.settings().cursor().heightPx());
    }

    /** Cursor with an explicit MiniMessage glyph and pixel height. */
    public CanvasCursor(Player viewer, String glyph, double heightPx) {
        super(0, 0, heightPx);
        this.glyph = glyph;
        this.renderer = new FakePacketCanvasRenderer(viewer, this);
    }

    /** Repositions the cursor onto the given canvas pixel (spawning it first if needed). */
    public void move(Canvas canvas, double pixelX, double pixelY) {
        if (!isValid()) {
            spawn(canvas);
        }

        // Lifted towards the viewer: text on the same plane would otherwise overlap the cursor at random.
        float depth = Pixelay.settings().text().normalOffset() + Pixelay.settings().cursor().depthOffset();
        ((FakePacketCanvasRenderer) renderer).move(canvas.pixelToWorld(pixelX, pixelY, depth).toVector());
    }

    /**
     * Hides the cursor glyph (e.g. when the player is not looking at the canvas); {@link #move}
     * respawns it the next time the player looks back.
     */
    public void hide() {
        despawn();
    }

    @Override
    public String renderText() {
        return glyph;
    }
}
