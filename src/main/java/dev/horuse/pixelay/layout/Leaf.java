package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.components.CanvasLayout;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.VAlign;
import java.util.List;

/**
 * A canvas component inside a layout. Its size is the measured text block (or the component's explicit
 * hitbox); inside the rectangle it gets, it is pinned by its own {@link #align(HAlign, VAlign)}
 * (centred by default).
 */
public final class Leaf extends Box {

    private final AbstractCanvasComponent component;
    private HAlign hAlign = HAlign.CENTER;
    private VAlign vAlign = VAlign.MIDDLE;

    Leaf(AbstractCanvasComponent component) {
        this.component = component;
    }

    public AbstractCanvasComponent component() {
        return component;
    }

    /** Where the component sits inside its rectangle, e.g. {@code LEFT/TOP} pins it to the top-left. */
    public Leaf align(HAlign hAlign, VAlign vAlign) {
        this.hAlign = hAlign;
        this.vAlign = vAlign;
        return this;
    }

    @Override
    protected double[] measureContent() {
        return CanvasLayout.hitboxSizePx(component, component.renderText());
    }

    @Override
    protected void arrangeContent(Rect content) {
        double x = switch (hAlign) {
            case LEFT -> content.x();
            case CENTER -> content.centerX();
            case RIGHT -> content.right();
        };
        double y = switch (vAlign) {
            case TOP -> content.y();
            case MIDDLE -> content.centerY();
            case BOTTOM -> content.bottom();
        };
        component.place(x, y, hAlign, vAlign);
    }

    @Override
    protected void collectComponents(List<AbstractCanvasComponent> out) {
        out.add(component);
    }
}
