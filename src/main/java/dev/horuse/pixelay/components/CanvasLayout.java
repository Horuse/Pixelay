package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.content.FontMetrics;

/**
 * Single source of placement geometry: translates the component's anchor point, its alignment, and
 * the measured text into entity pixel coordinates. TextDisplay always grows upwards from its
 * position (position = bottom of the block), Interaction is an AABB with the position at the
 * bottom-center, so both are built by a single formula {@code anchor + align offset}, and renderers
 * only consume the result.
 */
public final class CanvasLayout {

    /** Horizontal shift of TextDisplay text in the client renderer, in font px. */
    private static final double CLIENT_TEXT_OFFSET_FONT_PX = 1;

    private CanvasLayout() {}

    /**
     * @return text block height in canvas px (font line height * number of lines)
     */
    public static double textHeightPx(CanvasDrawable drawable, String text) {
        return textHeightPx(drawable, text, drawable.lineWidth());
    }

    /** Text block height when wrapped at {@code lineWidth} font px (instead of the drawable's own). */
    public static double textHeightPx(CanvasDrawable drawable, String text, int lineWidth) {
        // The client stacks lines with a 1 px gap, so every extra line adds a pitch, not a glyph height.
        int lines = Math.max(1, FontMetrics.lineCount(text, lineWidth));
        double fontPx = FontMetrics.LINE_HEIGHT_PX + (lines - 1) * FontMetrics.LINE_PITCH_PX;
        return (fontPx * drawable.heightPx()) / FontMetrics.LINE_HEIGHT_PX;
    }

    /**
     * @return gap between entity position and bottom ink edge, in canvas px. Configured in
     *     font-pixels ({@code text.bottom-gap-font-px}) so it scales with font size: the bottom
     *     line does not touch the entity position.
     */
    public static double bottomGapPx(CanvasDrawable drawable) {
        return (Pixelay.settings().text().bottomGapFontPx() * drawable.heightPx()) / FontMetrics.LINE_HEIGHT_PX;
    }

    /**
     * @return hitbox size {@code {width, height}} in canvas px — explicit component override if
     *     specified, otherwise the measured text block
     */
    public static double[] hitboxSizePx(CanvasDrawable drawable, String text) {
        return hitboxSizePx(drawable, text, drawable.lineWidth());
    }

    /** Hitbox size when the text is wrapped at {@code lineWidth} font px (used by layouts to measure). */
    public static double[] hitboxSizePx(CanvasDrawable drawable, String text, int lineWidth) {
        double width = drawable.hitboxWidthPx();
        double height = drawable.hitboxHeightPx();

        if (width > 0 && height > 0) {
            return new double[] { width, height };
        }

        return new double[] {
            FontMetrics.widthPx(text, drawable.heightPx(), lineWidth),
            textHeightPx(drawable, text, lineWidth),
        };
    }

    /**
     * @return entity position {@code {x, y}} in canvas px (bottom-center AABB) for a block of given
     *     size, anchored according to the component's alignment
     */
    public static double[] boxPosition(CanvasDrawable drawable, double boxWidthPx, double boxHeightPx) {
        return new double[] {
            drawable.pixelX() + drawable.hAlign().horizontalOffset(boxWidthPx),
            drawable.pixelY() + drawable.vAlign().bottomOffset(boxHeightPx),
        };
    }

    /**
     * @return TextDisplay spawn position in canvas px. The text block is always centered on the
     *     entity position (anchor — center-bottom), and the native TextDisplay alignment only
     *     defines the snapping of lines to each other inside the block. Therefore, horizontal
     *     anchor is compensated by shifting the entity by half the width of the measured text, and
     *     vertical — by the gap {@link #bottomGapPx} between the entity and the ink.
     */
    public static double[] displayPosition(CanvasDrawable drawable, String text) {
        double width = FontMetrics.widthPx(text, drawable.heightPx(), drawable.lineWidth());
        double height = textHeightPx(drawable, text);
        double fontPx = drawable.heightPx() / FontMetrics.LINE_HEIGHT_PX;
        double bottomOffset = drawable.vAlign().bottomOffset(height);
        // The bottom gap compensates the empty space under the glyphs, so it applies fully to a
        // bottom-anchored block, half to a middle one and not at all to a top-anchored one.
        double gapShare = height > 0 ? 1 - bottomOffset / height : 1;
        return new double[] {
            // The client draws a TextDisplay starting at (1 - width / 2), i.e. 1 font px right of centre.
            drawable.pixelX() + drawable.hAlign().horizontalOffset(width) - CLIENT_TEXT_OFFSET_FONT_PX * fontPx,
            drawable.pixelY() + bottomOffset + bottomGapPx(drawable) * gapShare,
        };
    }

    /**
     * @return true if the canvas point (px, py) falls within the component's hitbox
     */
    public static boolean contains(CanvasDrawable drawable, double[] hitboxSizePx, double px, double py) {
        double centerX = drawable.pixelX() + drawable.hAlign().horizontalOffset(hitboxSizePx[0]);
        double bottom = drawable.pixelY() + drawable.vAlign().bottomOffset(hitboxSizePx[1]);
        return Math.abs(px - centerX) <= hitboxSizePx[0] / 2 && py <= bottom && py >= bottom - hitboxSizePx[1];
    }
}
