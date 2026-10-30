package dev.horuse.pixelay.data;

/** An axis-aligned rectangle in canvas pixels. */
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

    /** Checks if point is inside rectangle using half-open bounds. */
    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    /** Calculates intersection of two rectangles, returning empty if disjoint. */
    public Rect intersect(Rect other) {
        double left = Math.max(x, other.x);
        double top = Math.max(y, other.y);
        double right = Math.min(right(), other.right());
        double bottom = Math.min(bottom(), other.bottom());
        return new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    /** Checks if this rectangle completely covers another rectangle. */
    public boolean covers(Rect other) {
        return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }
}
