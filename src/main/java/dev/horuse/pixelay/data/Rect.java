package dev.horuse.pixelay.data;

/** An axis-aligned rectangle in canvas px (y grows down). */
public record Rect(double x, double y, double width, double height) {
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

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

    /** The common part of both rectangles; empty (zero size) when they do not overlap. */
    public Rect intersect(Rect other) {
        double left = Math.max(x, other.x);
        double top = Math.max(y, other.y);
        double right = Math.min(right(), other.right());
        double bottom = Math.min(bottom(), other.bottom());
        return new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    /** Whether the rectangle covers {@code other} entirely. */
    public boolean covers(Rect other) {
        return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }
}
