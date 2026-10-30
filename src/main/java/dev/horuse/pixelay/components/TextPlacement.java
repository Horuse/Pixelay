package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.content.FontMetrics;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import net.kyori.adventure.text.Component;

/**
 * Calculates text placement and dimensions on a canvas. Computes the text bounding box,
 * TextDisplay entity coordinates, and component line breaks.
 * Renderers call {@link #place} to obtain placement data. Layouts call {@link #boxSizePx} to measure dimensions.
 */
public final class TextPlacement {

    /**
     * Maximum line width sent to the client to prevent client-side line wrapping.
     * Pixelay calculates and applies line breaks explicitly.
     */
    public static final int CLIENT_LINE_WIDTH = 1_000_000;

    /** Horizontal client TextDisplay renderer offset in font pixels. */
    private static final double CLIENT_TEXT_OFFSET_FONT_PX = 1;

    /** Trailing glyph advance spacing in font pixels. */
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

    /** Measures text layout and calculates entity position from element anchor and alignment. */
    public static Placement place(CanvasDrawable drawable, String text) {
        FontMetrics.Laid laid = FontMetrics.lay(CanvasText.parse(text), drawable.lineWidth(), drawable.textWrap());
        FontMetrics.Metrics metrics = laid.metrics();
        double baseFontPx = drawable.heightPx() / FontMetrics.LINE_HEIGHT_PX;
        double fontPxX = baseFontPx * drawable.visualScaleX();
        double fontPxY = baseFontPx * drawable.visualScaleY();
        double width = inkWidthPx(metrics, fontPxX);
        double height = (metrics.bottomPx() - metrics.topPx()) * fontPxY;
        double boxTop = drawable.pixelY() + drawable.vAlign().bottomOffset(height) - height;
        double boxCenterX = drawable.pixelX() + drawable.hAlign().horizontalOffset(width);
        // The client centers line advance 1 font pixel right of the entity and stacks lines upward.
        // The entity position aligns with the last line so the text fills the bounding box.
        double firstLineTop = boxTop - metrics.topPx() * fontPxY;
        double localDx = (TRAILING_SPACING_FONT_PX / 2.0 - CLIENT_TEXT_OFFSET_FONT_PX) * fontPxX;
        double localDy =
            firstLineTop +
            normalBottomPx(metrics) * fontPxY +
            bottomGapPx(drawable) * drawable.visualScaleY() -
            (boxTop + height / 2.0);
        double boxCenterY = boxTop + height / 2.0;
        double x = boxCenterX + localDx;
        double y = boxCenterY + localDy;
        return new Placement(
            explicitOr(drawable, width, height),
            x,
            y,
            laid.component(),
            laid,
            boxCenterX - width / 2,
            firstLineTop,
            fontPxX
        );
    }

    /** Calculates element dimensions {@code {width, height}} wrapped at {@code lineWidth} font pixels. */
    public static Size boxSizePx(CanvasDrawable drawable, String text, int lineWidth) {
        if (drawable.hitboxWidthPx() > 0 && drawable.hitboxHeightPx() > 0) {
            return new Size(drawable.hitboxWidthPx(), drawable.hitboxHeightPx());
        }
        FontMetrics.Metrics metrics = FontMetrics.measure(text, lineWidth, drawable.textWrap());
        double fontPx = drawable.heightPx() / FontMetrics.LINE_HEIGHT_PX;
        return new Size(inkWidthPx(metrics, fontPx), (metrics.bottomPx() - metrics.topPx()) * fontPx);
    }

    /**
     * Calculates the element bounding box in canvas pixels for clicks and background panels.
     */
    public static Rect bounds(CanvasDrawable drawable, Size size) {
        double centerX = drawable.pixelX() + drawable.hAlign().horizontalOffset(size.width());
        double bottom = drawable.pixelY() + drawable.vAlign().bottomOffset(size.height());
        return new Rect(centerX - size.width() / 2, bottom - size.height(), size.width(), size.height());
    }

    /** @return true if canvas coordinates lie within element bounds including edges */
    public static boolean contains(CanvasDrawable drawable, Size size, double px, double py) {
        Rect box = bounds(drawable, size);
        return px >= box.x() && px <= box.right() && py >= box.y() && py <= box.bottom();
    }

    /** Returns explicit hitbox dimensions when width and height are positive, or computed size otherwise. */
    private static Size explicitOr(CanvasDrawable drawable, double width, double height) {
        if (drawable.hitboxWidthPx() > 0 && drawable.hitboxHeightPx() > 0) {
            return new Size(drawable.hitboxWidthPx(), drawable.hitboxHeightPx());
        }
        return new Size(width, height);
    }

    /** Calculates ink width from line advance minus trailing font spacing. */
    private static double inkWidthPx(FontMetrics.Metrics metrics, double fontPx) {
        return Math.max(0, Math.round(metrics.maxWidthPx()) - TRAILING_SPACING_FONT_PX) * fontPx;
    }

    /** Calculates calibration gap in canvas pixels between the last line and the entity. */
    private static double bottomGapPx(CanvasDrawable drawable) {
        return (Pixelay.settings().text().bottomGapFontPx() * drawable.heightPx()) / FontMetrics.LINE_HEIGHT_PX;
    }

    /** Calculates bottom coordinate of the last line in font pixels from the top. */
    private static double normalBottomPx(FontMetrics.Metrics metrics) {
        return FontMetrics.LINE_HEIGHT_PX + (metrics.lines() - 1) * FontMetrics.LINE_PITCH_PX;
    }
}
