package dev.horuse.pixelay.data;

/** A two-dimensional size in canvas pixels. */
public record Size(double width, double height) {
    public static final Size ZERO = new Size(0, 0);

    /** Returns size along the main axis of a row or column. */
    public double main(boolean horizontal) {
        return horizontal ? width : height;
    }

    public double cross(boolean horizontal) {
        return horizontal ? height : width;
    }

    public static Size of(boolean horizontal, double main, double cross) {
        return horizontal ? new Size(main, cross) : new Size(cross, main);
    }
}
