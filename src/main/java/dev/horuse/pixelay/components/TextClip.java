package dev.horuse.pixelay.components;

import dev.horuse.pixelay.content.GlyphBox;
import dev.horuse.pixelay.content.LineAlign;
import dev.horuse.pixelay.data.Rect;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Cuts a text at its clip (CSS {@code overflow}), pixel by pixel, with the Pixelay core shader of the
 * generated resource pack. The server works out for every glyph how much of it lies outside and hides that in
 * the glyph's colour (its lowest bits); the shader reads it and drops the pixels outside. A text that lies
 * entirely inside its clip is sent as it is.
 * <p>
 * The code of a glyph is 9 bits: bit 8 is always set, bits 0-3 cut it horizontally and bits 4-7 vertically.
 * A cut value {@code k} of 1-7 drops the first {@code k/8} of the glyph (from the left, or the top), 9-15
 * keeps only its first {@code (k-8)/8}, 8 hides the glyph, 0 keeps it whole. A coded text is sent with
 * opacity {@link #CODED_OPACITY}: the shader decodes only texts with exactly that opacity.
 */
final class TextClip {

    /** Text opacity marking a coded text (one step below opaque: invisible). */
    static final int CODED_OPACITY = 254;
    /** The code of a glyph drawn whole. */
    private static final int WHOLE = 1 << 8;
    private static final int HIDDEN = 8;
    private static final int STEPS = 8;

    private TextClip() {}

    /**
     * @param component the text to send
     * @param coded whether it carries cut codes (send it with {@link #CODED_OPACITY})
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

    /** The glyph's quad in canvas px. */
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

    /** The cut value of a glyph spanning {@code [from, to)} along one axis, clipped to {@code [min, max)}. */
    private static int cut(double from, double to, double min, double max) {
        double size = to - from;
        if (to <= min || from >= max || size <= 0) {
            return HIDDEN;
        }
        if (min > from) {
            int dropped = (int) Math.round(((min - from) / size) * STEPS);
            return dropped >= STEPS ? HIDDEN : dropped;
        }
        if (max < to) {
            int kept = (int) Math.round(((max - from) / size) * STEPS);
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
