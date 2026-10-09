package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.AbstractCanvasComponent;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * A layout node with a CSS-like box model: optional fixed size, padding (inside the box), margin
 * (outside), grow factor and self alignment. Layout runs in two passes: {@link #measure()} computes
 * the natural size bottom-up, {@link #arrange(Rect)} assigns rectangles top-down.
 */
public abstract class Box implements LayoutItem {

    protected Insets padding = Insets.NONE;
    protected Insets margin = Insets.NONE;
    protected double fixedWidth = -1;
    protected double fixedHeight = -1;
    protected double grow;
    protected Align alignSelf;
    private String id;
    private Consumer<Player> onClick;
    private Rect rect = new Rect(0, 0, 0, 0);

    /** Natural content size {@code {width, height}} without padding. */
    protected abstract double[] measureContent();

    /** Places the children inside the content rectangle (the box rectangle minus padding). */
    protected abstract void arrangeContent(Rect content);

    /** Adds every canvas component of this subtree to {@code out}. */
    protected abstract void collectComponents(List<AbstractCanvasComponent> out);

    /** Adds every box of this subtree (self included, depth-first, parents before children). */
    protected void collectBoxes(List<Box> out) {
        out.add(this);
    }

    /** Outer size {@code {width, height}}: fixed size or content + padding. Margin is not included. */
    public final double[] measure() {
        double[] content = measureContent();
        double width = fixedWidth >= 0 ? fixedWidth : content[0] + padding.horizontal();
        double height = fixedHeight >= 0 ? fixedHeight : content[1] + padding.vertical();
        return new double[] { width, height };
    }

    public final void arrange(Rect rect) {
        this.rect = rect;
        arrangeContent(rect.inset(padding));
    }

    /** The rectangle assigned by the last layout pass. */
    public Rect rect() {
        return rect;
    }

    public String id() {
        return id;
    }

    public Consumer<Player> onClick() {
        return onClick;
    }

    @SuppressWarnings("unchecked")
    private <T extends Box> T self() {
        return (T) this;
    }

    public <T extends Box> T padding(double all) {
        return padding(Insets.all(all));
    }

    public <T extends Box> T padding(Insets padding) {
        this.padding = padding;
        return self();
    }

    public <T extends Box> T margin(Insets margin) {
        this.margin = margin;
        return self();
    }

    public <T extends Box> T width(double width) {
        this.fixedWidth = width;
        return self();
    }

    public <T extends Box> T height(double height) {
        this.fixedHeight = height;
        return self();
    }

    public <T extends Box> T size(double width, double height) {
        return width(width).height(height);
    }

    /** Share of the free space along the parent's main axis (CSS {@code flex-grow}). */
    public <T extends Box> T grow(double grow) {
        this.grow = grow;
        return self();
    }

    /** Overrides the parent's cross-axis alignment for this box (CSS {@code align-self}). */
    public <T extends Box> T alignSelf(Align align) {
        this.alignSelf = align;
        return self();
    }

    /** Tag for hit testing ({@link MountedLayout#idAt}), e.g. to highlight a hovered card. */
    public <T extends Box> T id(String id) {
        this.id = id;
        return self();
    }

    /** Makes the whole box clickable via {@link MountedLayout#click}. */
    public <T extends Box> T onClick(Consumer<Player> onClick) {
        this.onClick = onClick;
        return self();
    }
}
