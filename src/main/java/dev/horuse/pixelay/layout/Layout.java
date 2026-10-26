package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.components.CanvasBlock;
import dev.horuse.pixelay.components.CanvasItem;
import dev.horuse.pixelay.components.CanvasModel;
import dev.horuse.pixelay.content.TextContent;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.VAlign;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Entry point of the HTML/CSS-like layout DSL. Import statically and nest:
 * <pre>{@code
 * import static dev.horuse.pixelay.layout.Layout.*;
 *
 * MountedLayout ui = mount(
 *     column(
 *         row(text(viewer, 28, "<gold><b>Title"), spacer(), text(viewer, 20, "<green>$1,250")),
 *         spacer(),
 *         row(
 *             grid(cards).columns(repeat(3, px(220))).rows(repeat(3, px(136))),
 *             spacer(),
 *             button(viewer, 22, "<white><b>Continue", player -> {})
 *         ).align(Align.END)
 *     ).padding(32)
 * );
 * ui.spawn(canvas);
 * }</pre>
 */
public final class Layout {

    private Layout() {}

    // --- Containers (HTML <div>, Flexbox, Grid, Stack, ScrollView) ---------------------------------------

    /** An empty block box (like an empty HTML {@code <div>}), useful for panels, dividers, and shapes. */
    public static Box<?> box() {
        return new Box<>();
    }

    /** A vertical block container (HTML {@code <div>} / CSS {@code display: flex; flex-direction: column}). */
    public static Flex div(LayoutItem... children) {
        return new Flex(false, children);
    }

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

    // --- Text Nodes (HTML <p> / <span>) ------------------------------------------------------------------

    /** Creates a text leaf node for {@code viewer} (or a public server text when {@code viewer == null}). */
    public static Leaf text(Player viewer, double heightPx, String miniMessage) {
        return new Leaf(viewer, heightPx, miniMessage);
    }

    /** Creates a text leaf node with custom or dynamic {@link TextContent} (e.g. {@code () -> "..."}). */
    public static Leaf text(Player viewer, double heightPx, TextContent content) {
        return new Leaf(viewer, heightPx, content);
    }

    /** Creates a public server text leaf node visible to all players. */
    public static Leaf text(double heightPx, String miniMessage) {
        return new Leaf(null, heightPx, miniMessage);
    }

    /** Creates a public server text leaf node with custom or dynamic {@link TextContent}. */
    public static Leaf text(double heightPx, TextContent content) {
        return new Leaf(null, heightPx, content);
    }

    // --- Interactive Controls (HTML <button>, <input>, <progress>, <canvas>) -----------------------------

    /** Creates an interactive {@link Button} with explicit font height. */
    public static Button button(Player viewer, double heightPx, String miniMessage, Consumer<Player> onClick) {
        return new Button(viewer, heightPx, miniMessage, onClick);
    }

    /** Creates an interactive {@link Button} with dynamic {@link TextContent} and explicit font height. */
    public static Button button(Player viewer, double heightPx, TextContent content, Consumer<Player> onClick) {
        return new Button(viewer, heightPx, content, onClick);
    }

    /** Creates an interactive {@link Button} with default 20px font height. */
    public static Button button(Player viewer, String miniMessage, Consumer<Player> onClick) {
        return new Button(viewer, 20, miniMessage, onClick);
    }

    /** Creates a public server {@link Button} visible to all players. */
    public static Button button(double heightPx, String miniMessage, Consumer<Player> onClick) {
        return new Button(null, heightPx, miniMessage, onClick);
    }

    /** Creates an interactive {@link Checkbox} with a geometric indicator and optional text label. */
    public static Checkbox checkbox(
        Player viewer,
        String label,
        boolean checked,
        BiConsumer<Player, Boolean> onChange
    ) {
        return new Checkbox(viewer, label, checked, onChange);
    }

    /** Creates an interactive {@link Checkbox} without a text label. */
    public static Checkbox checkbox(boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Checkbox(checked, onChange);
    }

    /** Creates an interactive pill {@link Toggle} switch with a sliding thumb and optional text label. */
    public static Toggle toggle(Player viewer, String label, boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Toggle(viewer, label, checked, onChange);
    }

    /** Creates an interactive pill {@link Toggle} switch without a text label. */
    public static Toggle toggle(boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Toggle(checked, onChange);
    }

    /** Creates a static {@link ProgressBar} ({@code 0.0..1.0}). */
    public static ProgressBar progressBar(double progress) {
        return new ProgressBar(progress);
    }

    /** Creates a live dynamic {@link ProgressBar} backed by a {@link DoubleSupplier}. */
    public static ProgressBar progressBar(DoubleSupplier progressSupplier) {
        return new ProgressBar(progressSupplier);
    }

    /** Creates an HTML {@code <canvas>}-like 2D pixel grid and interactive drawing surface for {@code viewer}. */
    public static Canvas canvas(Player viewer, int gridWidth, int gridHeight) {
        return new Canvas(viewer, gridWidth, gridHeight);
    }

    /** Creates a public server {@link Canvas} visible to all players. */
    public static Canvas canvas(int gridWidth, int gridHeight) {
        return new Canvas(gridWidth, gridHeight);
    }

    /** Alias for {@link #canvas(Player, int, int)}. */
    public static Canvas pixelCanvas(Player viewer, int gridWidth, int gridHeight) {
        return new Canvas(viewer, gridWidth, gridHeight);
    }

    /** Alias for {@link #canvas(int, int)}. */
    public static Canvas pixelCanvas(int gridWidth, int gridHeight) {
        return new Canvas(gridWidth, gridHeight);
    }

    // --- 3D Models (Items & Blocks) ----------------------------------------------------------------------

    /** Creates a 3D item display node inside a layout from a {@link Material}. */
    public static ModelLeaf item(Player viewer, Material material, double sizePx) {
        return item(viewer, new ItemStack(material), sizePx);
    }

    /** Creates a 3D item display node inside a layout. */
    public static ModelLeaf item(Player viewer, ItemStack stack, double sizePx) {
        return item(viewer, () -> stack, sizePx);
    }

    /** Creates a dynamic 3D item display node inside a layout. */
    public static ModelLeaf item(Player viewer, Supplier<ItemStack> stack, double sizePx) {
        return new ModelLeaf(
            viewer != null ? CanvasItem.fake(viewer, 0, 0, sizePx, stack) : new CanvasItem(0, 0, sizePx, stack)
        );
    }

    /** Creates a 3D block display node inside a layout from a {@link Material}. */
    public static ModelLeaf block(Player viewer, Material material, double sizePx) {
        return block(viewer, material.createBlockData(), sizePx, sizePx);
    }

    /** Creates a 3D block display node with explicit width and height inside a layout from a {@link Material}. */
    public static ModelLeaf block(Player viewer, Material material, double widthPx, double heightPx) {
        return block(viewer, material.createBlockData(), widthPx, heightPx);
    }

    /** Creates a 3D block display node inside a layout. */
    public static ModelLeaf block(Player viewer, BlockData block, double sizePx) {
        return block(viewer, () -> block, sizePx, sizePx);
    }

    /** Creates a 3D block display node with explicit width and height inside a layout. */
    public static ModelLeaf block(Player viewer, BlockData block, double widthPx, double heightPx) {
        return block(viewer, () -> block, widthPx, heightPx);
    }

    /** Creates a dynamic 3D block display node with explicit width and height inside a layout. */
    public static ModelLeaf block(Player viewer, Supplier<BlockData> block, double widthPx, double heightPx) {
        return new ModelLeaf(
            viewer != null
                ? CanvasBlock.fake(viewer, 0, 0, widthPx, heightPx, block)
                : new CanvasBlock(0, 0, widthPx, heightPx, block)
        );
    }

    // --- Grid Tracks, Spacers & Transitions --------------------------------------------------------------

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

    // --- Legacy Node Wrappers & Mounting -----------------------------------------------------------------

    /** Identity pass-through so {@code node(box)} works seamlessly when migrating to box-based factories. */
    public static <B extends Box<B>> B node(B box) {
        return box;
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

    /** Wraps a layout tree bound to {@code viewer} for spawning on a canvas. */
    public static MountedLayout mount(Player viewer, LayoutItem root) {
        return new MountedLayout(box(root)).viewer(viewer);
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
