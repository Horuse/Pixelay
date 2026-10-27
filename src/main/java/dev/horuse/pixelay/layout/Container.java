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
            if (!child.isAbsolute() && !child.isHidden()) {
                width = Math.max(width, child.minOuterWidth());
            }
        }
        return width;
    }

    /** Returns only the children participating in normal layout flow (excluding {@link Position#ABSOLUTE} and {@link Box#isHidden()}). */
    protected List<Box<?>> flowChildren() {
        boolean anyExcluded = false;
        for (int i = 0; i < children.size(); i++) {
            Box<?> c = children.get(i);
            if (c.isAbsolute() || c.isHidden()) {
                anyExcluded = true;
                break;
            }
        }
        if (!anyExcluded) {
            return children;
        }
        List<Box<?>> flow = new ArrayList<>(children.size());
        for (Box<?> child : children) {
            if (!child.isAbsolute() && !child.isHidden()) {
                flow.add(child);
            }
        }
        return flow;
    }

    @Override
    void arrangeAbsoluteChildren(Rect bounds) {
        for (Box<?> child : children) {
            if (child.isHidden()) {
                child.arrange(new Rect(bounds.x(), bounds.y(), 0, 0), false);
                continue;
            }
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
            double h;
            if (hasT && hasB && !child.hasFixedHeight()) {
                h = Math.max(0, bounds.height() - child.top - child.bottom - m.vertical());
            } else if (child.heightPercent >= 0 && child.fixedHeight < 0) {
                h = Math.max(0, bounds.height() - m.vertical()) * child.heightPercent;
            } else {
                h = natural.height();
            }

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

            child.arrange(
                new Rect(
                    bounds.x() + (x - bounds.x()) * visualScaleX(),
                    bounds.y() + (y - bounds.y()) * visualScaleY(),
                    w * visualScaleX(),
                    h * visualScaleY()
                ),
                overflowsHere(),
                visualScaleX(),
                visualScaleY(),
                visualRotateDeg(),
                visualRotateXDeg(),
                visualRotateYDeg(),
                visualPivotX(),
                visualPivotY()
            );
        }
    }
}
