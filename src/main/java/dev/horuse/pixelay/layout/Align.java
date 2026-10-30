package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;

/**
 * Specifies alignment of a child element along an axis inside allocated space.
 * The {@link #STRETCH} option expands the child to fill available space.
 */
public enum Align {
    START,
    CENTER,
    END,
    STRETCH;

    /** @return offset of an item with the given size inside available space */
    double offset(double space, double size) {
        return switch (this) {
            case START, STRETCH -> 0;
            case CENTER -> (space - size) / 2;
            case END -> space - size;
        };
    }

    /** Positions an element inside a target area with alignment offsets. */
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
