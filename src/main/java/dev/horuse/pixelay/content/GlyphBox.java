package dev.horuse.pixelay.content;

/**
 * Bounding rectangle for a rendered glyph in font pixels relative to the block origin.
 */
public record GlyphBox(double left, double top, double right, double bottom) {
    public double width() {
        return right - left;
    }

    public double height() {
        return bottom - top;
    }
}
