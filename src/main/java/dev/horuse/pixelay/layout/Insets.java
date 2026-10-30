package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.data.Rect;

/** Represents padding or margin offsets in canvas pixels. */
public record Insets(double top, double right, double bottom, double left) {
    public static final Insets NONE = new Insets(0, 0, 0, 0);

    /** Creates equal insets on every side. */
    public static Insets all(double value) {
        return new Insets(value, value, value, value);
    }

    /** Creates insets with distinct vertical and horizontal values. */
    public static Insets of(double vertical, double horizontal) {
        return new Insets(vertical, horizontal, vertical, horizontal);
    }

    public static Insets top(double value) {
        return new Insets(value, 0, 0, 0);
    }

    public static Insets bottom(double value) {
        return new Insets(0, 0, value, 0);
    }

    public static Insets left(double value) {
        return new Insets(0, 0, 0, value);
    }

    public static Insets right(double value) {
        return new Insets(0, value, 0, 0);
    }

    double horizontal() {
        return left + right;
    }

    double vertical() {
        return top + bottom;
    }

    /** Shrinks a rectangle by these insets. */
    Rect shrink(Rect rect) {
        return new Rect(
            rect.x() + left,
            rect.y() + top,
            Math.max(0, rect.width() - horizontal()),
            Math.max(0, rect.height() - vertical())
        );
    }
}
