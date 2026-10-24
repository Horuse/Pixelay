package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.content.FontMetrics;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import net.kyori.adventure.text.Component;

/**
 * Where and how big a text element is on its canvas: the element's box (the drawn text, or an explicit
 * hitbox), where its TextDisplay must stand so the drawing fills that box, and the text with Pixelay's line
 * breaks. Renderers get all of it at once from {@link #place}; layouts measure with {@link #boxSizePx}.
 */
public final class TextPlacement {

    /**
     * Line width the client gets: far beyond any text, so it never wraps by itself; Pixelay writes the line
     * breaks into the text.
     */
    public static final int CLIENT_LINE_WIDTH = 1_000_000;

    /** Horizontal shift of TextDisplay text in the client renderer, in font px. */
    private static final double CLIENT_TEXT_OFFSET_FONT_PX = 1;

    /** Spacing the font keeps after the last glyph of a line (part of its advance, never drawn). */
    private static final int TRAILING_SPACING_FONT_PX = 1;

    private TextPlacement() {}

    /**
     * A placed text element.
     *
     * @param box the element's box size in canvas px
     * @param x TextDisplay position on the canvas, in canvas px
     * @param y TextDisplay position on the canvas, in canvas px
     * @param component the text to send, with its line breaks
     * @param laid the laid-out text, to find where each glyph is drawn
     * @param textLeft left edge of the drawn text block, in canvas px
     * @param firstLineTop top of the first line, in canvas px
     * @param fontPx canvas px per font px
     */
    public record Placement(
        Size box,
        double x,
        double y,
        Component component,
        FontMetrics.Laid laid,
        double textLeft,
        double firstLineTop,
        double fontPx
    ) {}

    /** Parses, wraps and measures the text once, and places the element by its anchor and alignment. */
    public static Placement place(CanvasDrawable drawable, String text) {
        FontMetrics.Laid laid = FontMetrics.lay(CanvasText.parse(text), drawable.lineWidth(), drawable.textWrap());
        FontMetrics.Metrics metrics = laid.metrics();
        double fontPx = drawable.heightPx() / FontMetrics.LINE_HEIGHT_PX;
        double width = inkWidthPx(metrics, fontPx);
        double height = (metrics.bottomPx() - metrics.topPx()) * fontPx;
        double boxTop = drawable.pixelY() + drawable.vAlign().bottomOffset(height) - height;
        double boxCenterX = drawable.pixelX() + drawable.hAlign().horizontalOffset(width);
        // The client centres the advance (ink plus the trailing spacing) 1 font px right of the entity and
        // stacks the lines up from it, so the entity stands at the end of the last normal line (plus the
        // configured gap), wherever the box is anchored: the drawing always fills the box.
        double firstLineTop = boxTop - metrics.topPx() * fontPx;
        double x = boxCenterX + (TRAILING_SPACING_FONT_PX / 2.0 - CLIENT_TEXT_OFFSET_FONT_PX) * fontPx;
        double y = firstLineTop + normalBottomPx(metrics) * fontPx + bottomGapPx(drawable);
        return new Placement(
            explicitOr(drawable, width, height),
            x,
            y,
            laid.component(),
            laid,
            boxCenterX - width / 2,
            firstLineTop,
            fontPx
        );
    }

    /** The element's box size {@code {width, height}} when wrapped at {@code lineWidth} font px (for layouts). */
    public static Size boxSizePx(CanvasDrawable drawable, String text, int lineWidth) {
        if (drawable.hitboxWidthPx() > 0 && drawable.hitboxHeightPx() > 0) {
            return new Size(drawable.hitboxWidthPx(), drawable.hitboxHeightPx());
        }
        FontMetrics.Metrics metrics = FontMetrics.measure(text, lineWidth, drawable.textWrap());
        double fontPx = drawable.heightPx() / FontMetrics.LINE_HEIGHT_PX;
        return new Size(inkWidthPx(metrics, fontPx), (metrics.bottomPx() - metrics.topPx()) * fontPx);
    }

    /**
     * The element's box in canvas px, for a box of {@code size}: anchored by the element's alignment. Clicks and
     * backgrounds use it.
     */
    public static Rect bounds(CanvasDrawable drawable, Size size) {
        double centerX = drawable.pixelX() + drawable.hAlign().horizontalOffset(size.width());
        double bottom = drawable.pixelY() + drawable.vAlign().bottomOffset(size.height());
        return new Rect(centerX - size.width() / 2, bottom - size.height(), size.width(), size.height());
    }

    /** Whether a canvas point is on the element's box, its edges included. */
    public static boolean contains(CanvasDrawable drawable, Size size, double px, double py) {
        Rect box = bounds(drawable, size);
        return px >= box.x() && px <= box.right() && py >= box.y() && py <= box.bottom();
    }

    /** An explicit hitbox counts only when both sides are set. */
    private static Size explicitOr(CanvasDrawable drawable, double width, double height) {
        if (drawable.hitboxWidthPx() > 0 && drawable.hitboxHeightPx() > 0) {
            return new Size(drawable.hitboxWidthPx(), drawable.hitboxHeightPx());
        }
        return new Size(width, height);
    }

    /** Drawn width: the widest line's advance minus the trailing spacing, so the box ends where the ink ends. */
    private static double inkWidthPx(FontMetrics.Metrics metrics, double fontPx) {
        return Math.max(0, Math.round(metrics.maxWidthPx()) - TRAILING_SPACING_FONT_PX) * fontPx;
    }

    /** {@code text.bottom-gap-font-px} in canvas px: calibration between the last line and the entity. */
    private static double bottomGapPx(CanvasDrawable drawable) {
        return (Pixelay.settings().text().bottomGapFontPx() * drawable.heightPx()) / FontMetrics.LINE_HEIGHT_PX;
    }

    /** Bottom of the last line as if every glyph had the normal height, in font px from the first line's top. */
    private static double normalBottomPx(FontMetrics.Metrics metrics) {
        return FontMetrics.LINE_HEIGHT_PX + (metrics.lines() - 1) * FontMetrics.LINE_PITCH_PX;
    }
}
