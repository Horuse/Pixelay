package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.config.PixelaySettings;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.VAlign;
import dev.horuse.pixelay.debug.Overflow;
import dev.horuse.pixelay.layout.LayoutItem;
import java.util.function.Consumer;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

/**
 * Common state for pixel-positioned components: anchor pixel coordinates, pixel height, alignment,
 * the backing {@link CanvasRenderer} and the shared canvas tag used for world cleanup ({@link
 * Pixelay#entityKey()}). Geometry ({@link CanvasDrawable}) is owned here, rendering is delegated to
 * the renderer; layout math lives in {@link CanvasLayout}.
 */
public abstract class AbstractCanvasComponent implements CanvasComponent, CanvasDrawable, LayoutItem {

    protected double pixelX;
    protected double pixelY;
    protected double heightPx;

    protected HAlign hAlign = HAlign.CENTER;
    protected VAlign vAlign = VAlign.BOTTOM;
    protected double hitboxWidthPx = -1;
    protected double hitboxHeightPx = -1;

    protected Canvas canvas;
    protected CanvasRenderer renderer;
    private int layoutVersion;
    // Layout state, written by layouts and read by renderers; both run on the main server thread,
    // volatile keeps them consistent if a renderer reads off-thread.
    private volatile Overflow overflow = Overflow.NONE;
    /** Line width set by a layout, in font px; {@code <= 0} means the configured default. */
    private volatile int layoutLineWidth;

    protected AbstractCanvasComponent(double pixelX, double pixelY, double heightPx) {
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.heightPx = heightPx;
    }

    @Override
    public void spawn(Canvas canvas) {
        this.canvas = canvas;
        renderer.spawn(canvas);
    }

    @Override
    public void despawn() {
        renderer.despawn();
    }

    @Override
    public boolean isValid() {
        return renderer.isValid();
    }

    @Override
    public void refresh() {
        renderer.refresh();
    }

    /**
     * Pins the anchor point to the given edges of the element's box: e.g. {@code align(HAlign.LEFT,
     * VAlign.TOP)} makes {@code (pixelX, pixelY)} the top-left corner of the rendered block,
     * whatever its measured size is.
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractCanvasComponent> T align(HAlign hAlign, VAlign vAlign) {
        this.hAlign = hAlign;
        this.vAlign = vAlign;
        layoutVersion++;
        return (T) this;
    }

    /**
     * Overrides the click/hit-test box size in canvas px (by default the box is the measured text
     * block).
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractCanvasComponent> T hitbox(double widthPx, double heightPx) {
        this.hitboxWidthPx = widthPx;
        this.hitboxHeightPx = heightPx;
        layoutVersion++;
        return (T) this;
    }

    /**
     * @return measured hitbox of the rendered text {@code {width, height}} in canvas px, or null
     *     before spawn
     */
    public double[] measuredHitboxPx() {
        return renderer != null ? renderer.measuredHitboxPx() : null;
    }

    /** @return the only player who sees this element, or {@code null} when everyone does */
    public Player viewer() {
        return renderer != null ? renderer.viewer() : null;
    }

    /**
     * Moves the anchor point and alignment; used by layouts. Renderers pick the change up on the next
     * refresh, even if the text stayed the same.
     */
    public void place(double pixelX, double pixelY, HAlign hAlign, VAlign vAlign) {
        if (this.pixelX == pixelX && this.pixelY == pixelY && this.hAlign == hAlign && this.vAlign == vAlign) {
            return;
        }

        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.hAlign = hAlign;
        this.vAlign = vAlign;
        layoutVersion++;
    }

    /**
     * Set by layouts: wrap the text at this many font px (the width the layout gives the element),
     * capped by the configured line width; {@code <= 0} restores the default.
     */
    public void setLineWidth(int fontPx) {
        int width = fontPx > 0 ? Math.min(fontPx, Pixelay.settings().text().lineWidth()) : 0;
        // No layoutVersion bump: a pass may measure several times; renderers compare the final value.
        layoutLineWidth = width;
    }

    @Override
    public int lineWidth() {
        return layoutLineWidth > 0 ? layoutLineWidth : CanvasDrawable.super.lineWidth();
    }

    /** Set by layouts: the element is bigger than the space it was given (see LayoutDebug). */
    public void setOverflow(Overflow overflow) {
        this.overflow = overflow;
    }

    @Override
    public Overflow overflow() {
        return overflow;
    }

    @Override
    public int layoutVersion() {
        return layoutVersion;
    }

    @Override
    public double pixelX() {
        return pixelX;
    }

    @Override
    public double pixelY() {
        return pixelY;
    }

    @Override
    public double heightPx() {
        return heightPx;
    }

    @Override
    public HAlign hAlign() {
        return hAlign;
    }

    @Override
    public VAlign vAlign() {
        return vAlign;
    }

    @Override
    public double hitboxWidthPx() {
        return hitboxWidthPx;
    }

    @Override
    public double hitboxHeightPx() {
        return hitboxHeightPx;
    }

    @Override
    public Consumer<Player> onClick() {
        return null;
    }

    /**
     * Applies the shared text style from config.yml ({@code text.*}) to a server-side TextDisplay.
     */
    protected static void applyTextStyle(TextDisplay display) {
        PixelaySettings.Text style = Pixelay.settings().text();
        display.setShadowed(style.shadow());
        display.setSeeThrough(style.seeThrough());
        // The configured background is drawn as a separate panel (TextBackground).
        display.setBackgroundColor(Color.fromARGB(0));
        display.setLineWidth(style.lineWidth());
        display.setViewRange(style.viewRange());
        if (style.fullBrightness()) {
            display.setBrightness(new Display.Brightness(15, 15));
        }
    }

    /** Tags an entity as canvas-owned and marks it non-persistent. */
    protected static void tag(Entity entity) {
        entity.getPersistentDataContainer().set(Pixelay.entityKey(), PersistentDataType.BYTE, (byte) 1);
        entity.setPersistent(false);
    }
}
