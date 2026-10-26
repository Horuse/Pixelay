package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;

/**
 * Layers sharing one area (like a single CSS grid cell): each child is placed by its own
 * {@code justifySelf}/{@code alignSelf} and fills the area by default (unless it has a fixed size).
 */
public final class Stack extends Container<Stack> {

    Stack(LayoutItem... items) {
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
        for (Box<?> child : flowChildren()) {
            Align horizontal = child.justifySelf != null ? child.justifySelf : Align.STRETCH;
            Align vertical = child.alignSelf != null ? child.alignSelf : Align.STRETCH;
            Rect area = Align.place(
                content,
                child.outer(content.width()),
                horizontal,
                vertical,
                horizontal == Align.STRETCH && !child.hasFixedWidth(),
                vertical == Align.STRETCH && !child.hasFixedHeight()
            );
            child.arrange(child.effectiveMargin().shrink(area), overflowsHere());
        }
    }
}
