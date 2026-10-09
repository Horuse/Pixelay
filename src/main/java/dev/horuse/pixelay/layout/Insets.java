package dev.horuse.pixelay.layout;

/** Padding or margin in canvas px, CSS order: top, right, bottom, left. */
public record Insets(double top, double right, double bottom, double left) {
    public static final Insets NONE = new Insets(0, 0, 0, 0);

    /** Same value on every side. */
    public static Insets all(double value) {
        return new Insets(value, value, value, value);
    }

    /** {@code vertical} on top/bottom, {@code horizontal} on left/right. */
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
}
