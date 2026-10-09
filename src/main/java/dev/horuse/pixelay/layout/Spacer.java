package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.AbstractCanvasComponent;
import java.util.List;

/** Empty space; by default takes all free space along the parent's main axis. */
public final class Spacer extends Box {

    Spacer(double size) {
        if (size >= 0) {
            fixedWidth = size;
            fixedHeight = size;
        } else {
            grow = 1;
        }
    }

    @Override
    protected double[] measureContent() {
        return new double[] { 0, 0 };
    }

    @Override
    protected void arrangeContent(Rect content) {}

    @Override
    protected void collectComponents(List<AbstractCanvasComponent> out) {}
}
