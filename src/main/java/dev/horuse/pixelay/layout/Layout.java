package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.components.CanvasModel;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.VAlign;
import java.util.Arrays;

/**
 * Entry point of the layout DSL. Import statically and nest:
 * <pre>{@code
 * import static dev.horuse.pixelay.layout.Layout.*;
 *
 * MountedLayout ui = mount(
 *     column(
 *         row(title, spacer(), balance),
 *         spacer(),
 *         row(grid(cards).columns(repeat(3, px(220))).rows(repeat(3, px(136))), spacer(), continueButton)
 *             .align(Align.END)
 *     ).padding(32)
 * );
 * ui.spawn(canvas);
 * }</pre>
 * Canvas components can be passed directly; texts become {@link Leaf leaves}, items and blocks
 * {@link ModelLeaf model leaves}.
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

    /** A scrollable vertical container with an optional scrollbar. */
    public static ScrollView scrollView(LayoutItem... children) {
        return new ScrollView(children);
    }

    /** A scrollable container wrapping {@code content}. */
    public static ScrollView scrollView(Box<?> content) {
        return new ScrollView(content);
    }

    /** Grid track of a fixed size in canvas px. */
    public static Track px(double size) {
        return new Track(Track.Kind.PX, size);
    }

    /** Grid track taking a share of the free space (CSS {@code fr}). */
    public static Track fr(double fraction) {
        return new Track(Track.Kind.FR, fraction);
    }

    /** Grid track sized by its content (CSS {@code auto}). */
    public static Track auto() {
        return Track.AUTO;
    }

    /** {@code count} copies of a track (CSS {@code repeat(count, track)}). */
    public static Track[] repeat(int count, Track track) {
        Track[] tracks = new Track[Math.max(0, count)];
        Arrays.fill(tracks, track);
        return tracks;
    }

    /** Takes all free space along the parent's main axis. */
    public static Spacer spacer() {
        return new Spacer(-1);
    }

    /** Fixed empty space of {@code size} px. */
    public static Spacer space(double size) {
        return new Spacer(size);
    }

    /** Creates a transition definition with the given duration in milliseconds and easing timing function. */
    public static Transition transition(long durationMs, Easing easing) {
        return Transition.all(durationMs, easing);
    }

    /** Creates a transition definition with the given duration in milliseconds and standard {@link Easing#EASE}. */
    public static Transition transition(long durationMs) {
        return Transition.all(durationMs, Easing.EASE);
    }

    /** Creates a transition definition for a specific property. */
    public static Transition transition(Transition.Property property, long durationMs, Easing easing) {
        return new Transition(property, durationMs, easing);
    }

    /** A component as a leaf, to set its box properties or its alignment inside its rectangle. */
    public static Leaf node(AbstractCanvasComponent component) {
        return new Leaf(component);
    }

    /** An item or a block as a leaf, to set its box properties or its alignment inside its rectangle. */
    public static ModelLeaf node(CanvasModel<?> model) {
        return new ModelLeaf(model);
    }

    /** Wraps a layout tree for spawning on a canvas; it fills the whole canvas. */
    public static MountedLayout mount(LayoutItem root) {
        return new MountedLayout(box(root));
    }

    /**
     * Wraps a layout tree placed at a canvas point instead of filling the canvas: the tree gets its natural
     * size and is aligned to {@code (x, y)} like a single component ({@code CENTER}/{@code MIDDLE} centres
     * it on the point). If a box in the tree is marked {@link Box#anchor()}, that box is aligned to the
     * point instead and the rest of the tree follows it.
     */
    public static MountedLayout mountAt(double x, double y, HAlign h, VAlign v, LayoutItem root) {
        return new MountedLayout(box(root), new MountedLayout.Placement(x, y, h, v));
    }

    static Box<?> box(LayoutItem item) {
        if (item instanceof Box<?> box) {
            return box;
        }

        if (item instanceof AbstractCanvasComponent component) {
            return new Leaf(component);
        }

        if (item instanceof CanvasModel<?> model) {
            return new ModelLeaf(model);
        }

        throw new IllegalArgumentException("Unsupported layout item: " + item);
    }
}
