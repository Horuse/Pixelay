package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import java.util.List;

/**
 * Empty space along the main axis of a row or a column: {@code spacer()} takes all free space,
 * {@code space(px)} a fixed length. It never adds size on the cross axis, and outside a row or a
 * column it is empty.
 */
public final class Spacer extends Box<Spacer> {

    private final double length;

    Spacer(double length) {
        this.length = length;
        if (length < 0) {
            grow = 1;
        }
        shrink = 0;
    }

    /** Fixed length along the parent row/column main axis (0 for a flexible spacer). */
    double mainLength() {
        return Math.max(0, length);
    }

    @Override
    protected Size measureContent(double maxWidth) {
        return Size.ZERO;
    }

    @Override
    protected double minContentWidth() {
        return 0;
    }

    @Override
    protected void arrangeContent(Rect content) {}

    @Override
    protected void collectComponents(List<AbstractCanvasComponent> out) {}
}
