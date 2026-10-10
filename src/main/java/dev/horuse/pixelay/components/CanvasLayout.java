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
            // The client paints the text background 1 font px wider (left) and taller (top) than the
            // text: the element's box is that visible block, so layouts and click zones match it.
            FontMetrics.widthPx(text, drawable.heightPx(), lineWidth) + backgroundPadPx(drawable),
            textHeightPx(drawable, text, lineWidth) + backgroundPadPx(drawable),
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
    /** The 1 font px of background the client paints left of and above a TextDisplay's text, in canvas px. */
    public static double backgroundPadPx(CanvasDrawable drawable) {
        return drawable.heightPx() / FontMetrics.LINE_HEIGHT_PX;
    }

    /**
     * @return TextDisplay spawn position in canvas px. The element's box is the visible background:
     * the text plus 1 font px on the left and on top. The box is anchored by the element's alignment;
     * the text sits in its right/bottom part. The client draws the text 1 font px right of the entity
     * centre, and the bottom gap ({@link #bottomGapPx}) compensates the empty space under the glyphs
     * (fully for a bottom anchor, half for middle, none for top).
     */
    public static double[] displayPosition(CanvasDrawable drawable, String text) {
        double pad = backgroundPadPx(drawable);
        double width = FontMetrics.widthPx(text, drawable.heightPx(), drawable.lineWidth()) + pad;
        double height = textHeightPx(drawable, text) + pad;
        double fontPx = drawable.heightPx() / FontMetrics.LINE_HEIGHT_PX;
        double bottomOffset = drawable.vAlign().bottomOffset(height);
        double gapShare = height > 0 ? 1 - bottomOffset / height : 1;
        double boxCenterX = drawable.pixelX() + drawable.hAlign().horizontalOffset(width);
        return new double[] {
            // text centre is half a pad right of the box centre; the client adds 1 font px on its own
            boxCenterX + pad / 2 - CLIENT_TEXT_OFFSET_FONT_PX * fontPx,
            drawable.pixelY() + bottomOffset + bottomGapPx(drawable) * gapShare,
        };
    }

    /**
     * @return true if the canvas point (px, py) falls within the component's hitbox
     */
    /**
     * The clickable box {@code {left, top, right, bottom}} in canvas px: the hitbox, which for plain
     * text is the visible background block (see {@link #hitboxSizePx}).
     */
    public static double[] clickBounds(CanvasDrawable drawable, double[] hitboxSizePx) {
        double centerX = drawable.pixelX() + drawable.hAlign().horizontalOffset(hitboxSizePx[0]);
        double bottom = drawable.pixelY() + drawable.vAlign().bottomOffset(hitboxSizePx[1]);
        return new double[] {
            centerX - hitboxSizePx[0] / 2,
            bottom - hitboxSizePx[1],
            centerX + hitboxSizePx[0] / 2,
            bottom,
        };
    }

    public static boolean contains(CanvasDrawable drawable, double[] hitboxSizePx, double px, double py) {
        double[] box = clickBounds(drawable, hitboxSizePx);
        return px >= box[0] && px <= box[2] && py >= box[1] && py <= box[3];
    }
}
