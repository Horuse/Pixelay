package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.clicks.CanvasClicks;
import dev.horuse.pixelay.clicks.ClickTarget;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.debug.LayoutDebug;
import dev.horuse.pixelay.utils.DisplayUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

/**
 * Draws a {@link CanvasDrawable} as per-player entities: the backing {@link TextDisplay} is spawned in
 * the world but made invisible by default and shown only to a single
 * {@link Player} viewer through Paper's per-player entity visibility (which sends the
 * spawn/metadata packets to that viewer only). This gives the same look as the server renderer
 * while keeping the canvas fully private to one player — e.g. for a menu screen where concurrent
 * viewers must not see each other's interface.
 */
public class PacketCanvasRenderer implements CanvasRenderer {

    private final Player viewer;
    private final CanvasDrawable drawable;

    private Canvas canvas;
    private TextDisplay display;
    private String lastRendered;
    private int lastLayoutVersion;
    private int lastLineWidth;
    private final TextBackground background;
    private double[] measuredHitbox;
    /** Click target of a clickable element: its exact box on the canvas. */
    private final ClickTarget clickTarget = this::clickAt;

    public PacketCanvasRenderer(Player viewer, CanvasDrawable drawable) {
        this.viewer = viewer;
        this.drawable = drawable;
        this.background = new TextBackground(viewer);
    }

    @Override
    public void spawn(Canvas canvas) {
        if (isValid()) {
            return;
        }

        this.canvas = canvas;

        String text = drawable.renderText();
        measuredHitbox = CanvasLayout.hitboxSizePx(drawable, text);
        double[] pos = CanvasLayout.displayPosition(drawable, text);
        Location loc = canvas.pixelToWorld(pos[0], pos[1], drawable.textDepth());
        lastRendered = text;
        lastLayoutVersion = drawable.layoutVersion();
        lastLineWidth = drawable.lineWidth();
        display = DisplayUtils.spawnText(
            loc,
            CanvasLayout.component(drawable, text),
            canvas.contentRotation(),
            canvas.scaleForHeightPx(drawable.heightPx()),
            drawable.hAlign().textAlignment()
        );
        display.setVisibleByDefault(false);
        viewer.showEntity(Pixelay.plugin(), display);
        AbstractCanvasComponent.applyTextStyle(display);
        display.setLineWidth(CanvasLayout.CLIENT_LINE_WIDTH);
        syncBackground();
        AbstractCanvasComponent.tag(display);

        if (drawable.onClick() != null) {
            CanvasClicks.register(canvas, viewer, clickTarget);
        }
    }

    @Override
    public void despawn() {
        background.remove();
        if (display != null) {
            display.remove();
            display = null;
        }

        if (canvas != null) {
            CanvasClicks.unregister(canvas, viewer, clickTarget);
        }
    }

    @Override
    public boolean isValid() {
        if (display == null || !display.isValid()) {
            return false;
        }

        return true;
    }

    @Override
    public void refresh() {
        if (display == null || !display.isValid()) {
            return;
        }

        String rendered = drawable.renderText();

        if (
            rendered.equals(lastRendered) &&
            drawable.layoutVersion() == lastLayoutVersion &&
            drawable.lineWidth() == lastLineWidth
        ) {
            syncBackground();
            return;
        }

        lastRendered = rendered;
        lastLayoutVersion = drawable.layoutVersion();
        lastLineWidth = drawable.lineWidth();
        // Position first, then text: so the block doesn't blink
        // with the old position under the new text.
        measuredHitbox = CanvasLayout.hitboxSizePx(drawable, rendered);
        double[] pos = CanvasLayout.displayPosition(drawable, rendered);
        display.teleport(canvas.pixelToWorld(pos[0], pos[1], drawable.textDepth()));
        display.setLineWidth(CanvasLayout.CLIENT_LINE_WIDTH);
        display.text(CanvasLayout.component(drawable, rendered));
        syncBackground();
    }

    @Override
    public double[] measuredHitboxPx() {
        return measuredHitbox;
    }

    @Override
    public Player viewer() {
        return viewer;
    }

    /** Background panel: the debug highlight (blue / red / orange) while debugging, the configured one otherwise. */
    private void syncBackground() {
        background.sync(
            canvas,
            drawable,
            measuredHitbox,
            TextBackground.colorOf(drawable, LayoutDebug.isEnabled(viewer))
        );
    }

    private boolean clickAt(double px, double py, Player player) {
        double[] hitbox = measuredHitbox;
        if (drawable.onClick() == null || hitbox == null || !CanvasLayout.contains(drawable, hitbox, px, py)) {
            return false;
        }
        drawable.onClick().accept(player);
        return true;
    }
}
