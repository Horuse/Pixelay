package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;

/**
 * Layers sharing one area (like a single CSS grid cell): each child is placed by its own
 * {@code justifySelf}/{@code alignSelf} and fills the area by default (unless it has a fixed size).
 */
public final class Stack extends Container<Stack> {

    public Stack(LayoutItem... items) {
        super(items);
    }

    @Override
    public String tagName() {
        return "stack";
    }

    @Override
    protected Size measureContent(double maxWidth) {
        double width = 0;
        double height = 0;
        for (Box<?> child : flowChildren()) {
            Size size = child.outer(maxWidth);
            width = Math.max(width, size.width());
            height = Math.max(height, size.height());
        }
        return new Size(width, height);
    }

    @Override
    protected double minContentWidth() {
        return widestChildMinWidth();
    }

    @Override
    protected void arrangeContent(Rect content) {
        double sx = visualScaleX();
        double sy = visualScaleY();
        Rect unscaledContent = new Rect(
            content.x(),
            content.y(),
            sx > 1e-4 ? content.width() / sx : content.width(),
            sy > 1e-4 ? content.height() / sy : content.height()
        );
        for (Box<?> child : flowChildren()) {
            Align horizontal = child.justifySelf != null ? child.justifySelf : Align.STRETCH;
            Align vertical = child.alignSelf != null ? child.alignSelf : Align.STRETCH;
            Rect area = Align.place(
                unscaledContent,
                child.outer(unscaledContent.width()),
                horizontal,
                vertical,
                horizontal == Align.STRETCH && !child.hasFixedWidth(),
                vertical == Align.STRETCH && !child.hasFixedHeight()
            );
            Rect unscaledSlot = child.effectiveMargin().shrink(area);
            Rect scaledSlot = new Rect(
                content.x() + (unscaledSlot.x() - content.x()) * sx,
                content.y() + (unscaledSlot.y() - content.y()) * sy,
                unscaledSlot.width() * sx,
                unscaledSlot.height() * sy
            );
            arrangeChild(child, scaledSlot, overflowsHere());
        }
    }
}
