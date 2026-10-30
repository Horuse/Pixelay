package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.content.TextContent;
import dev.horuse.pixelay.content.TextWrap;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.CanvasResolution;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import dev.horuse.pixelay.data.VAlign;
import dev.horuse.pixelay.debug.CanvasBoundsDebug;
import dev.horuse.pixelay.debug.Fit;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

/**
 * Common state for pixel-positioned components. Contains anchor coordinates, height, alignment,
 * the backing {@link CanvasRenderer}, and the canvas cleanup tag ({@link Pixelay#entityKey()}).
 * This class stores geometry ({@link CanvasDrawable}) and delegates rendering to the renderer.
 * {@link TextPlacement} handles text placement.
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
    // Layout state written by layouts and read by renderers on the main thread.
    // The volatile modifier maintains consistency if a renderer reads asynchronously.
    private volatile Fit fit = Fit.NONE;
    /** Line width in font pixels set by a layout. Values {@code <= 0} use the configured default. */
    private volatile int layoutLineWidth;
    /** Depth step of the text background set by layouts in painting order. The text renders one step above. */
    private volatile int layerStep = Layers.DEFAULT_TEXT_BACKGROUND_STEP;
    /** Indicates whether the pointer hovers over the element in its layout (CSS {@code :hover}). */
    private volatile boolean hovered;
    private volatile TextWrap textWrap = TextWrap.NORMAL;
    /** Text that replaces the component's primary text from a state style, or {@code null}. */
    private volatile TextContent textOverride;
    /** Maximum width in font pixels before ellipsis truncation. Values {@code <= 0} disable truncation. */
    private volatile int ellipsisWidthFontPx;
    /** Bounding clip rectangle for layout drawing, or {@code null} outside layouts. */
    private volatile Rect layoutClip;
    private volatile double visualScaleX = 1.0;
    private volatile double visualScaleY = 1.0;
    private volatile double rotateDeg;
    private volatile double rotateXDeg;
    private volatile double rotateYDeg;
    private volatile double rotatePivotX = Double.NaN;
    private volatile double rotatePivotY = Double.NaN;
    private volatile int transformationDuration;

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
     * Pins the anchor point to specified edges of the element box.
     * For example, {@code align(HAlign.LEFT, VAlign.TOP)} sets {@code (pixelX, pixelY)} as the top-left corner.
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractCanvasComponent> T align(HAlign hAlign, VAlign vAlign) {
        this.hAlign = hAlign;
        this.vAlign = vAlign;
        layoutVersion++;
        return (T) this;
    }

    /**
     * Overrides the click hitbox size in canvas pixels. By default, the hitbox matches the measured text block.
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractCanvasComponent> T hitbox(double widthPx, double heightPx) {
        this.hitboxWidthPx = widthPx;
        this.hitboxHeightPx = heightPx;
        layoutVersion++;
        return (T) this;
    }

    /**
     * @return measured hitbox of the rendered text {@code {width, height}} in canvas pixels, or null before spawn
     */
    public Size measuredHitboxPx() {
        return renderer != null ? renderer.measuredHitboxPx() : null;
    }

    @Override
    public Player viewer() {
        return renderer != null ? renderer.viewer() : null;
    }

    /**
     * Updates the anchor coordinates and alignment for layouts.
     * Renderers apply the change during the next refresh.
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
     * Sets the wrap width in font pixels based on layout constraints.
     * Pixelay clamps the value to the configured line width. Values {@code <= 0} restore the default.
     */
    @ApiStatus.Internal
    public void setLineWidth(int fontPx) {
        int width = fontPx > 0 ? Math.min(fontPx, Pixelay.settings().text().lineWidth()) : 0;
        // Do not increment layoutVersion because passes measure multiple times. Renderers compare final values.
        layoutLineWidth = width;
    }

    @Override
    public int lineWidth() {
        return layoutLineWidth > 0 ? layoutLineWidth : CanvasDrawable.super.lineWidth();
    }

    /** Controls line wrapping (CSS {@code white-space}). */
    @SuppressWarnings("unchecked")
    public <T extends AbstractCanvasComponent> T whiteSpace(TextWrap.WhiteSpace value) {
        textWrap = textWrap.withWhiteSpace(value);
        layoutVersion++;
        return (T) this;
    }

    /** Controls whether words break inside lines (CSS {@code word-break}). */
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

    /** @return true if the pointer hovers over this element box in its layout (CSS {@code :hover}) */
    public boolean isHovered() {
        return hovered;
    }

    /** Sets override text from a hover style, or {@code null}. */
    @ApiStatus.Internal
    public void setTextOverride(TextContent content) {
        this.textOverride = content;
    }

    protected TextContent textOverride() {
        return textOverride;
    }

    @ApiStatus.Internal
    public void setEllipsisWidthFontPx(int fontPx) {
        if (this.ellipsisWidthFontPx != fontPx) {
            this.ellipsisWidthFontPx = fontPx;
            layoutVersion++;
        }
    }

    protected int ellipsisWidthFontPx() {
        return ellipsisWidthFontPx;
    }

    @Override
    @ApiStatus.Internal
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    /** Sets the depth step of this element text background. Text renders one step above. */
    @Override
    @ApiStatus.Internal
    public void setLayerStep(int step) {
        // Renderers read depth via textDepth(), so no layout version update is required.
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

    @Override
    @ApiStatus.Internal
    public void setClip(Rect clip) {
        layoutClip = clip;
    }

    /** Returns the layout clip rectangle constrained to canvas boundaries. */
    @Override
    public Rect clip() {
        if (canvas == null) {
            return layoutClip;
        }
        CanvasResolution res = canvas.resolution();
        Rect whole = new Rect(0, 0, res.pixelWidth(), res.pixelHeight());
        return layoutClip != null ? layoutClip.intersect(whole) : whole;
    }

    /** Sets overflow status when the element exceeds allocated space. */
    @ApiStatus.Internal
    public void setFit(Fit fit) {
        this.fit = fit;
    }

    @Override
    public Fit fit() {
        return fit;
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

    /** Updates the rendered font height in canvas pixels (CSS {@code font-size}). */
    public void setHeightPx(double heightPx) {
        if (this.heightPx != heightPx) {
            this.heightPx = heightPx;
            layoutVersion++;
        }
    }

    /** Updates the primary text content of this component. */
    public void setContent(TextContent content) {}

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

    @ApiStatus.Internal
    public void setVisualTransform(
        double scaleX,
        double scaleY,
        double rotateDeg,
        double rotateXDeg,
        double rotateYDeg,
        double pivotX,
        double pivotY
    ) {
        if (
            Math.abs(this.visualScaleX - scaleX) < 1e-4 &&
            Math.abs(this.visualScaleY - scaleY) < 1e-4 &&
            Math.abs(this.rotateDeg - rotateDeg) < 1e-3 &&
            Math.abs(this.rotateXDeg - rotateXDeg) < 1e-3 &&
            Math.abs(this.rotateYDeg - rotateYDeg) < 1e-3 &&
            (Math.abs(rotateDeg) < 1e-3 ||
                (Math.abs(this.rotatePivotX - pivotX) < 1e-2 && Math.abs(this.rotatePivotY - pivotY) < 1e-2))
        ) {
            return;
        }
        this.visualScaleX = scaleX;
        this.visualScaleY = scaleY;
        this.rotateDeg = rotateDeg;
        this.rotateXDeg = rotateXDeg;
        this.rotateYDeg = rotateYDeg;
        this.rotatePivotX = pivotX;
        this.rotatePivotY = pivotY;
        layoutVersion++;
    }

    @Override
    public double visualScaleX() {
        return visualScaleX;
    }

    @Override
    public double visualScaleY() {
        return visualScaleY;
    }

    @Override
    public double rotateDeg() {
        return rotateDeg;
    }

    @Override
    public double rotateXDeg() {
        return rotateXDeg;
    }

    @Override
    public double rotateYDeg() {
        return rotateYDeg;
    }

    @Override
    public void setTransformationDuration(int ticks) {
        this.transformationDuration = Math.max(0, ticks);
    }

    @Override
    public int transformationDuration() {
        return transformationDuration;
    }

    @Override
    public double rotatePivotX() {
        return rotatePivotX;
    }

    @Override
    public double rotatePivotY() {
        return rotatePivotY;
    }
}
