package dev.horuse.pixelay.components;

import dev.horuse.pixelay.clicks.CanvasClicks;
import dev.horuse.pixelay.clicks.ClickTarget;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.debug.LayoutDebug;
import dev.horuse.pixelay.utils.DisplayUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

/**
 * Draws a {@link CanvasDrawable} as ordinary server entities visible to everyone: a {@link
 * TextDisplay} for the text. A clickable element is registered in {@link CanvasClicks}, which hit-tests
 * its exact box.
 * Clickable hitboxes are registered in {@link CanvasButton#CLICK_REGISTRY}. Positioning and hitbox
 * sizing are computed by {@link CanvasLayout} from the drawable's anchor and alignment.
 */
public class ServerCanvasRenderer implements CanvasRenderer {

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

    public ServerCanvasRenderer(CanvasDrawable drawable) {
        this.drawable = drawable;
        this.background = new TextBackground(null);
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
            CanvasText.parse(text),
            canvas.contentRotation(),
            canvas.scaleForHeightPx(drawable.heightPx()),
            drawable.hAlign().textAlignment()
        );
        AbstractCanvasComponent.applyTextStyle(display);
        display.setLineWidth(drawable.lineWidth());
        syncBackground();
        AbstractCanvasComponent.tag(display);

        if (drawable.onClick() != null) {
            CanvasClicks.register(canvas, null, clickTarget);
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
            CanvasClicks.unregister(canvas, null, clickTarget);
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
        // First the position, then the text: so that the block doesn't blink
        // with the old position under the new text.
        measuredHitbox = CanvasLayout.hitboxSizePx(drawable, rendered);
        double[] pos = CanvasLayout.displayPosition(drawable, rendered);
        display.teleport(canvas.pixelToWorld(pos[0], pos[1], drawable.textDepth()));
        display.setLineWidth(drawable.lineWidth());
        display.text(CanvasText.parse(rendered));
        syncBackground();
    }

    @Override
    public double[] measuredHitboxPx() {
        return measuredHitbox;
    }

    /** Background panel: the debug highlight (blue / red / orange) while debugging, the configured one otherwise. */
    private void syncBackground() {
        background.sync(canvas, drawable, measuredHitbox, TextBackground.colorOf(drawable, LayoutDebug.anyEnabled()));
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
