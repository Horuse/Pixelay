package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.LayoutComponent;
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

    public List<Box<?>> children() {
        return List.copyOf(children);
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
            width = Math.max(width, child.minOuterWidth());
        }
        return width;
    }
}
