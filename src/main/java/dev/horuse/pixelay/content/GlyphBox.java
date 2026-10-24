package dev.horuse.pixelay.content;

/**
 * Where one glyph of a laid-out text is drawn, in font px from the left of the text block and the top of its
 * first line (y grows down).
 */
public record GlyphBox(double left, double top, double right, double bottom) {
    public double width() {
        return right - left;
    }

    public double height() {
        return bottom - top;
    }
}
