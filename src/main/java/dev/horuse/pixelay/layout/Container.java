package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.LayoutComponent;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import java.util.ArrayList;
import java.util.List;

/** A box with children. */
public abstract class Container<S extends Container<S>> extends Box<S> {

    protected final List<Box<?>> children = new ArrayList<>();
    protected double gap;

    Container(LayoutItem... items) {
        for (LayoutItem item : items) {
            children.add(Layout.box(item));
        }
    }

    /** Space between neighbouring children (CSS {@code gap}). */
    public S gap(double gap) {
        this.gap = gap;
        return self();
    }

    public double gap() {
        return gap;
    }

    public List<Box<?>> children() {
        return List.copyOf(children);
    }

    /** Appends a child item to this container and invalidates cached measurements. */
    public S addChild(LayoutItem item) {
        children.add(Layout.box(item));
        invalidate();
        return self();
    }

    /** Removes a child box from this container and invalidates cached measurements. */
    public S removeChild(Box<?> child) {
        if (children.remove(child)) {
            invalidate();
        }
        return self();
    }

    /** Removes all children from this container and invalidates cached measurements. */
    public S clearChildren() {
        if (!children.isEmpty()) {
            children.clear();
            invalidate();
        }
        return self();
    }

    @Override
    protected void invalidate() {
        super.invalidate();
        children.forEach(Box::invalidate);
    }

    @Override
    protected void collectComponents(List<LayoutComponent> out) {
        children.forEach(child -> child.collectComponents(out));
    }

    @Override
    protected void collectBoxes(List<Box<?>> out) {
        super.collectBoxes(out);
        children.forEach(child -> child.collectBoxes(out));
    }

    /** Widest child min-content width (for containers that stack children on top of each other). */
    protected double widestChildMinWidth() {
        double width = 0;
        for (Box<?> child : children) {
            if (!child.isAbsolute()) {
                width = Math.max(width, child.minOuterWidth());
            }
        }
        return width;
    }

    /** Returns only the children participating in normal layout flow (excluding {@link Position#ABSOLUTE}). */
    protected List<Box<?>> flowChildren() {
        boolean anyAbsolute = false;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).isAbsolute()) {
                anyAbsolute = true;
                break;
            }
        }
        if (!anyAbsolute) {
            return children;
        }
        List<Box<?>> flow = new ArrayList<>(children.size());
        for (Box<?> child : children) {
            if (!child.isAbsolute()) {
                flow.add(child);
            }
        }
        return flow;
    }

    @Override
    void arrangeAbsoluteChildren(Rect bounds) {
        for (Box<?> child : children) {
            if (!child.isAbsolute()) {
                continue;
            }
            Insets m = child.effectiveMargin();
            boolean hasL = !Double.isNaN(child.left);
            boolean hasR = !Double.isNaN(child.right);
            boolean hasT = !Double.isNaN(child.top);
            boolean hasB = !Double.isNaN(child.bottom);

            double availW =
                hasL && hasR
                    ? Math.max(0, bounds.width() - child.left - child.right - m.horizontal())
                    : Math.max(0, bounds.width() - m.horizontal());
            Size natural = child.measure(availW);

            double w = hasL && hasR && !child.hasFixedWidth() ? availW : natural.width();
            double h =
                hasT && hasB && !child.hasFixedHeight()
                    ? Math.max(0, bounds.height() - child.top - child.bottom - m.vertical())
                    : natural.height();

            double x;
            if (hasL) {
                x = bounds.x() + child.left + m.left();
            } else if (hasR) {
                x = bounds.right() - child.right - m.right() - w;
            } else {
                Align hAlign = child.justifySelf != null ? child.justifySelf : Align.START;
                x = bounds.x() + m.left() + hAlign.offset(Math.max(0, bounds.width() - m.horizontal()), w);
            }

            double y;
            if (hasT) {
                y = bounds.y() + child.top + m.top();
            } else if (hasB) {
                y = bounds.bottom() - child.bottom - m.bottom() - h;
            } else {
                Align vAlign = child.alignSelf != null ? child.alignSelf : Align.START;
                y = bounds.y() + m.top() + vAlign.offset(Math.max(0, bounds.height() - m.vertical()), h);
            }

            child.arrange(new Rect(x, y, w, h), overflowsHere());
        }
    }
}
