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

    public boolean contains(double px, double py) {
        return px >= x && px <= right() && py >= y && py <= bottom();
    }

    Rect inset(Insets insets) {
        return new Rect(
            x + insets.left(),
            y + insets.top(),
            Math.max(0, width - insets.horizontal()),
            Math.max(0, height - insets.vertical())
        );
    }
}
