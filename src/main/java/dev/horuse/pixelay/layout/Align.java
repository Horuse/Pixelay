package dev.horuse.pixelay.layout;

/**
 * Placement of a child on an axis inside the space given to it (CSS {@code align-items} /
 * {@code justify-items}). {@link #STRETCH} makes the child fill that space, like the CSS default.
 */
public enum Align {
    START,
    CENTER,
    END,
    STRETCH;

    /** @return offset of a {@code size} item inside {@code space} */
    double offset(double space, double size) {
        return switch (this) {
            case START, STRETCH -> 0;
            case CENTER -> (space - size) / 2;
            case END -> space - size;
        };
    }
}
