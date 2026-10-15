package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.content.TextContent;
import dev.horuse.pixelay.content.TextWrap;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.data.Size;
import dev.horuse.pixelay.data.VAlign;
import dev.horuse.pixelay.debug.CanvasBoundsDebug;
import dev.horuse.pixelay.debug.Overflow;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

/**
 * Common state for pixel-positioned components: anchor pixel coordinates, pixel height, alignment,
 * the backing {@link CanvasRenderer} and the shared canvas tag used for world cleanup ({@link
 * Pixelay#entityKey()}). Geometry ({@link CanvasDrawable}) is owned here, rendering is delegated to
 * the renderer; text placement lives in {@link TextPlacement}.
 */
public abstract class AbstractCanvasComponent implements LayoutComponent, CanvasDrawable {

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
    /** Step of the text background (the text is one step above), set by layouts in painting order. */
    private volatile int layerStep = Layers.DEFAULT_TEXT_BACKGROUND_STEP;
    /** Under the pointer in its layout (CSS {@code :hover}). */
    private volatile boolean hovered;
    private volatile TextWrap textWrap = TextWrap.NORMAL;
    /** Text replacing the component's own one (a state style), or {@code null}. */
    private volatile TextContent textOverride;

    protected AbstractCanvasComponent(double pixelX, double pixelY, double heightPx) {
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.heightPx = heightPx;
    }

    @Override
    public void spawn(Canvas canvas) {
        this.canvas = canvas;
        renderer.spawn(canvas);
        CanvasBoundsDebug.track(canvas);
    }

    @Override
    public void despawn() {
        renderer.despawn();
        if (canvas != null) {
            CanvasBoundsDebug.untrack(canvas);
        }
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
    public Size measuredHitboxPx() {
        return renderer != null ? renderer.measuredHitboxPx() : null;
    }

    @Override
    public Player viewer() {
        return renderer != null ? renderer.viewer() : null;
    }

    /**
     * Moves the anchor point and alignment; used by layouts. Renderers pick the change up on the next
     * refresh, even if the text stayed the same.
     */
    @ApiStatus.Internal
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
    @ApiStatus.Internal
    public void setLineWidth(int fontPx) {
        int width = fontPx > 0 ? Math.min(fontPx, Pixelay.settings().text().lineWidth()) : 0;
        // No layoutVersion bump: a pass may measure several times; renderers compare the final value.
        layoutLineWidth = width;
    }

    @Override
    public int lineWidth() {
        return layoutLineWidth > 0 ? layoutLineWidth : CanvasDrawable.super.lineWidth();
    }

    /** CSS {@code white-space}: whether the text wraps to its width at all. */
    @SuppressWarnings("unchecked")
    public <T extends AbstractCanvasComponent> T whiteSpace(TextWrap.WhiteSpace value) {
        textWrap = textWrap.withWhiteSpace(value);
        layoutVersion++;
        return (T) this;
    }

    /** CSS {@code word-break} / {@code overflow-wrap}: whether a word may break inside. */
    @SuppressWarnings("unchecked")
    public <T extends AbstractCanvasComponent> T wordBreak(TextWrap.WordBreak value) {
        textWrap = textWrap.withWordBreak(value);
        layoutVersion++;
        return (T) this;
    }

    @Override
    public TextWrap textWrap() {
        return textWrap;
    }

    /** Whether the pointer is over this element's box in its layout (CSS {@code :hover}). */
    public boolean isHovered() {
        return hovered;
    }

    /** Set by layouts: text replacing the component's own one (a hover style), or {@code null}. */
    @ApiStatus.Internal
    public void setTextOverride(TextContent content) {
        this.textOverride = content;
    }

    protected TextContent textOverride() {
        return textOverride;
    }

    @Override
    @ApiStatus.Internal
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    /** Set by layouts: the depth step of this element's text background (the text is one step above). */
    @Override
    @ApiStatus.Internal
    public void setLayerStep(int step) {
        // Renderers notice it through textDepth(): no layout version bump needed.
        layerStep = step;
    }

    @Override
    public float textDepth() {
        return Layers.at(layerStep + 1, viewer());
    }

    @Override
    public float textBackgroundDepth() {
        return Layers.at(layerStep, viewer());
    }

    /** Set by layouts: the element is bigger than the space it was given (see LayoutDebug). */
    @ApiStatus.Internal
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
}
