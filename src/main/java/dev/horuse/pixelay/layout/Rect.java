package dev.horuse.pixelay.layout;

/** An axis-aligned rectangle in canvas px (y grows down). */
public record Rect(double x, double y, double width, double height) {
    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public double centerX() {
        return x + width / 2;
    }

    public double centerY() {
        return y + height / 2;
    }

    /** Half-open: the right and bottom edges belong to the next box, so neighbours never overlap. */
    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    Rect inset(Insets insets) {
        return new Rect(
            x + insets.left(),
            y + insets.top(),
            Math.max(0, width - insets.horizontal()),
            Math.max(0, height - insets.vertical())
        );
    }

    /**
     * A box of {@code size} placed inside this area: on each axis it fills the area when stretched,
     * otherwise keeps its own size and is offset by the alignment.
     */
    Rect place(Size size, Align horizontal, Align vertical, boolean stretchWidth, boolean stretchHeight) {
        double w = stretchWidth ? width : Math.min(width, size.width());
        double h = stretchHeight ? height : Math.min(height, size.height());
        return new Rect(x + horizontal.offset(width, w), y + vertical.offset(height, h), w, h);
    }
}
