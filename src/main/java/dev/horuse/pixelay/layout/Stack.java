package dev.horuse.pixelay.layout;

/** Layers: every child gets the whole content rectangle (minus its margin), e.g. corner marks over a panel. */
public final class Stack extends Container {

    Stack(LayoutItem... items) {
        super(items);
    }

    @Override
    protected double[] measureContent() {
        double width = 0;
        double height = 0;
        for (Box child : children) {
            double[] size = outer(child);
            width = Math.max(width, size[0]);
            height = Math.max(height, size[1]);
        }
        return new double[] { width, height };
    }

    @Override
    protected void arrangeContent(Rect content) {
        for (Box child : children) {
            child.arrange(content.inset(child.margin));
        }
    }
}
