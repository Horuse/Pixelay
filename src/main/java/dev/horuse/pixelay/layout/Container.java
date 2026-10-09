package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.AbstractCanvasComponent;
import java.util.ArrayList;
import java.util.List;

/** A box with children. */
public abstract class Container extends Box {

    protected final List<Box> children = new ArrayList<>();
    protected double gap;

    Container(LayoutItem... items) {
        for (LayoutItem item : items) {
            children.add(Layout.box(item));
        }
    }

    /** Space between neighbouring children (CSS {@code gap}). */
    public <T extends Container> T gap(double gap) {
        this.gap = gap;
        @SuppressWarnings("unchecked")
        T self = (T) this;
        return self;
    }

    public List<Box> children() {
        return List.copyOf(children);
    }

    @Override
    protected void collectComponents(List<AbstractCanvasComponent> out) {
        children.forEach(child -> child.collectComponents(out));
    }

    @Override
    protected void collectBoxes(List<Box> out) {
        super.collectBoxes(out);
        children.forEach(child -> child.collectBoxes(out));
    }

    /** Outer size of a child plus its margin. */
    static double[] outer(Box child) {
        double[] size = child.measure();
        return new double[] { size[0] + child.margin.horizontal(), size[1] + child.margin.vertical() };
    }
}
