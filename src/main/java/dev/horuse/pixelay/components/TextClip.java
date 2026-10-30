package dev.horuse.pixelay.components;

import dev.horuse.pixelay.content.GlyphBox;
import dev.horuse.pixelay.content.LineAlign;
import dev.horuse.pixelay.data.Rect;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Clips text at overflow boundaries using the Pixelay core shader.
 * The server computes glyph coverage and encodes clipping instructions into low color bits.
 * The shader reads these bits and discards pixels outside boundaries.
 * Text inside bounds transfers without changes.
 * <p>
 * Glyph encoding uses 9 bits. Bit 8 is set, bits 0-3 clip horizontally, and bits 4-7 clip vertically.
 * Cut values from 1 to 7 drop {@code k/8} of the glyph. Values from 9 to 15 keep {@code (k-8)/8}.
 * Value 8 hides the glyph. Value 0 keeps the glyph complete.
 * Coded text uses opacity {@link #CODED_OPACITY}.
 */
final class TextClip {

    /** Text opacity marker for coded text. */
    static final int CODED_OPACITY = 254;
    /** Mask code for an unclipped glyph. */
    private static final int WHOLE = 1 << 8;
    private static final int HIDDEN = 8;
    private static final int STEPS = 8;

    private TextClip() {}

    /**
     * @param component the text component to send
     * @param coded true if text contains clip codes, false otherwise
     */
    record Clipped(Component component, boolean coded) {}

    static Clipped apply(CanvasDrawable drawable, TextPlacement.Placement placement) {
        Rect clip = drawable.clip();
        if (clip == null) {
            return new Clipped(placement.component(), false);
        }
        List<GlyphBox> boxes = placement.laid().glyphBoxes(lineAlign(drawable));
        int[] codes = new int[boxes.size()];
        boolean cut = false;
        for (int i = 0; i < boxes.size(); i++) {
            GlyphBox glyph = boxes.get(i);
            codes[i] = glyph != null ? code(onCanvas(glyph, placement), clip) : WHOLE;
            cut |= codes[i] != WHOLE;
        }
        return cut ? new Clipped(placement.laid().encoded(codes), true) : new Clipped(placement.component(), false);
    }

    /** Calculates the glyph rectangle in canvas pixels. */
    private static Rect onCanvas(GlyphBox glyph, TextPlacement.Placement placement) {
        double f = placement.fontPx();
        return new Rect(
            placement.textLeft() + glyph.left() * f,
            placement.firstLineTop() + glyph.top() * f,
            glyph.width() * f,
            glyph.height() * f
        );
    }

    private static int code(Rect glyph, Rect clip) {
        int horizontal = cut(glyph.x(), glyph.right(), clip.x(), clip.right());
        int vertical = cut(glyph.y(), glyph.bottom(), clip.y(), clip.bottom());
        if (horizontal == HIDDEN || vertical == HIDDEN) {
            return WHOLE | HIDDEN;
        }
        return WHOLE | horizontal | (vertical << 4);
    }

    /** Calculates the cut value of a glyph interval clipped to bounds. */
    private static int cut(double from, double to, double min, double max) {
        double size = to - from;
        if (to <= min || from >= max || size <= 0) {
            return HIDDEN;
        }
        if (min > from) {
            int dropped = (int) Math.ceil(((min - from) / size) * STEPS);
            return dropped >= STEPS ? HIDDEN : dropped;
        }
        if (max < to) {
            int kept = (int) Math.floor(((max - from) / size) * STEPS);
            return kept <= 0 ? HIDDEN : kept >= STEPS ? 0 : STEPS + kept;
        }
        return 0;
    }

    private static LineAlign lineAlign(CanvasDrawable drawable) {
        return switch (drawable.hAlign()) {
            case LEFT -> LineAlign.LEFT;
            case CENTER -> LineAlign.CENTER;
            case RIGHT -> LineAlign.RIGHT;
        };
    }
}
