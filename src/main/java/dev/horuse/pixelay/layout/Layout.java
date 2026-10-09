package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.VAlign;

/**
 * Entry point of the layout DSL. Import statically and nest:
 * <pre>{@code
 * import static dev.horuse.pixelay.layout.Layout.*;
 *
 * MountedLayout ui = mount(
 *     column(
 *         row(title, spacer(), balance),
 *         spacer(),
 *         row(grid(cards).rows(3).cell(220, 124), spacer(), continueButton).align(Align.END)
 *     ).padding(32)
 * );
 * ui.spawn(canvas);
 * }</pre>
 * Canvas components can be passed directly; they become {@link Leaf leaves}.
 */
public final class Layout {

    private Layout() {}

    public static Flex row(LayoutItem... children) {
        return new Flex(true, children);
    }

    public static Flex column(LayoutItem... children) {
        return new Flex(false, children);
    }

    public static Stack stack(LayoutItem... children) {
        return new Stack(children);
    }

    public static Grid grid(LayoutItem... children) {
        return new Grid(children);
    }

    /** Takes all free space along the parent's main axis. */
    public static Spacer spacer() {
        return new Spacer(-1);
    }

    /** Fixed empty space of {@code size} px. */
    public static Spacer space(double size) {
        return new Spacer(size);
    }

    /** A component as a leaf, to set its box properties or its alignment inside its rectangle. */
    public static Leaf node(AbstractCanvasComponent component) {
        return new Leaf(component);
    }

    /** A component pinned by the given edges inside its rectangle. */
    public static Leaf node(AbstractCanvasComponent component, HAlign hAlign, VAlign vAlign) {
        return new Leaf(component).align(hAlign, vAlign);
    }

    /** Wraps a layout tree for spawning on a canvas; it fills the whole canvas. */
    public static MountedLayout mount(LayoutItem root) {
        return new MountedLayout(box(root));
    }

    static Box box(LayoutItem item) {
        if (item instanceof Box box) {
            return box;
        }

        if (item instanceof AbstractCanvasComponent component) {
            return new Leaf(component);
        }

        throw new IllegalArgumentException("Unsupported layout item: " + item);
    }
}
