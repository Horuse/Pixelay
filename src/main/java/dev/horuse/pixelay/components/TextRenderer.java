package dev.horuse.pixelay.components;

import dev.horuse.pixelay.clicks.CanvasClicks;
import dev.horuse.pixelay.clicks.ClickTarget;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.Size;
import dev.horuse.pixelay.debug.LayoutDebug;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * What every text renderer shares: placing the text ({@link TextPlacement}), redrawing only when the text,
 * the placement or the depth changed, the background panel and click registration. Subclasses only create,
 * update and remove their entity.
 */
abstract class TextRenderer implements CanvasRenderer {

    protected final CanvasDrawable drawable;
    /** The only player who sees the element, or {@code null} for everyone. */
    protected final Player viewer;
    protected Canvas canvas;
    private final TextBackground background;
    private final ClickTarget clickTarget = this::clickAt;
    private TextPlacement.Placement placement;
    private String lastText;
    private int lastLayoutVersion;
    private int lastLineWidth;
    private float lastDepth;

    TextRenderer(CanvasDrawable drawable, Player viewer) {
        this.drawable = drawable;
        this.viewer = viewer;
        this.background = new TextBackground(viewer);
    }

    /** Creates the entity showing {@code placement}. */
    protected abstract void spawnEntity(TextPlacement.Placement placement, Location at);

    /** Moves the entity and changes its text. */
    protected abstract void updateEntity(TextPlacement.Placement placement, Location at);

    protected abstract void removeEntity();

    protected abstract boolean entityValid();

    @Override
    public final void spawn(Canvas canvas) {
        if (isValid()) {
            return;
        }
        this.canvas = canvas;
        String text = drawable.renderText();
        placement = TextPlacement.place(drawable, text);
        remember(text);
        spawnEntity(placement, location());
        syncBackground();
        if (drawable.onClick() != null) {
            CanvasClicks.register(canvas, viewer, clickTarget);
        }
    }

    @Override
    public final void refresh() {
        if (!isValid()) {
            return;
        }
        String text = drawable.renderText();
        if (unchanged(text)) {
            syncBackground();
            return;
        }
        placement = TextPlacement.place(drawable, text);
        remember(text);
        updateEntity(placement, location());
        syncBackground();
    }

    @Override
    public final void despawn() {
        background.remove();
        removeEntity();
        if (canvas != null) {
            CanvasClicks.unregister(canvas, viewer, clickTarget);
        }
    }

    @Override
    public final boolean isValid() {
        return entityValid();
    }

    @Override
    public final Size measuredHitboxPx() {
        return placement != null ? placement.box() : null;
    }

    @Override
    public final Player viewer() {
        return viewer;
    }

    /** The world location of the current placement, on the element's depth layer. */
    protected final Location location() {
        return canvas.pixelToWorld(placement.x(), placement.y(), drawable.textDepth());
    }

    protected final TextPlacement.Placement placement() {
        return placement;
    }

    /** The debug highlight while debugging (by the viewer, or anyone for shared entities), the configured one otherwise. */
    protected final void syncBackground() {
        boolean debugging = viewer != null ? LayoutDebug.isEnabled(viewer) : LayoutDebug.anyEnabled();
        background.sync(canvas, drawable, measuredHitboxPx(), TextBackground.colorOf(drawable, debugging));
    }

    private boolean unchanged(String text) {
        return (
            text.equals(lastText) &&
            drawable.layoutVersion() == lastLayoutVersion &&
            drawable.lineWidth() == lastLineWidth &&
            drawable.textDepth() == lastDepth
        );
    }

    private void remember(String text) {
        lastText = text;
        lastLayoutVersion = drawable.layoutVersion();
        lastLineWidth = drawable.lineWidth();
        lastDepth = drawable.textDepth();
    }

    private boolean clickAt(double px, double py, Player player) {
        Size box = measuredHitboxPx();
        if (drawable.onClick() == null || box == null || !TextPlacement.contains(drawable, box, px, py)) {
            return false;
        }
        drawable.onClick().accept(player);
        return true;
    }
}
