package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;

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

    /**
     * A box of {@code size} placed inside {@code area}: on each axis it fills the area when stretched, otherwise
     * keeps its own size and is offset by the alignment.
     */
    static Rect place(
        Rect area,
        Size size,
        Align horizontal,
        Align vertical,
        boolean stretchWidth,
        boolean stretchHeight
    ) {
        double w = stretchWidth ? area.width() : Math.min(area.width(), size.width());
        double h = stretchHeight ? area.height() : Math.min(area.height(), size.height());
        return new Rect(
            area.x() + horizontal.offset(area.width(), w),
            area.y() + vertical.offset(area.height(), h),
            w,
            h
        );
    }
}
