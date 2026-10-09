package dev.horuse.pixelay.layout;

/** A width/height pair in canvas px. */
public record Size(double width, double height) {
    static final Size ZERO = new Size(0, 0);

    /** Size along the main axis of a row ({@code horizontal}) or a column. */
    double main(boolean horizontal) {
        return horizontal ? width : height;
    }

    double cross(boolean horizontal) {
        return horizontal ? height : width;
    }

    static Size of(boolean horizontal, double main, double cross) {
        return horizontal ? new Size(main, cross) : new Size(cross, main);
    }
}
