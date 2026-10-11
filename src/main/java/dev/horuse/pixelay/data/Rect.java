package dev.horuse.pixelay.data;

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
}
