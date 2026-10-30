package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.components.CanvasBlock;
import dev.horuse.pixelay.components.CanvasItem;
import dev.horuse.pixelay.content.TextContent;
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
 * Factory helpers and unit constructors for the layout system.
 */
public final class Layout {

    private Layout() {}

    // --- Containers (HTML <div>, Flexbox, Grid, Stack, ScrollView) ---------------------------------------

    /** Creates an empty box element. */
    public static Box<?> box() {
        return new Box<>();
    }

    /** Creates a vertical column container. */
    public static Column div(LayoutItem... children) {
        return new Column(children);
    }

    public static Row row(LayoutItem... children) {
        return new Row(children);
    }

    public static Column column(LayoutItem... children) {
        return new Column(children);
    }

    public static Stack stack(LayoutItem... children) {
        return new Stack(children);
    }

    public static Grid grid(LayoutItem... children) {
        return new Grid(children);
    }

    /** Creates a scrollable vertical container with an optional scrollbar. */
    public static ScrollView scrollView(LayoutItem... children) {
        return new ScrollView(children);
    }

    /** Creates a scrollable container wrapping the specified content. */
    public static ScrollView scrollView(Box<?> content) {
        return new ScrollView(content);
    }

    // --- Text Nodes (HTML <p> / <span>) ------------------------------------------------------------------

    /** Creates a text element for the viewing player. */
    public static Text text(Player viewer, double heightPx, String miniMessage) {
        return new Text(viewer, heightPx, miniMessage);
    }

    /** Creates a text element with dynamic text content for the viewing player. */
    public static Text text(Player viewer, double heightPx, TextContent content) {
        return new Text(viewer, heightPx, content);
    }

    /** Creates a text element with default session binding. */
    public static Text text(double heightPx, String miniMessage) {
        return new Text(heightPx, miniMessage);
    }

    /** Creates a text element with dynamic text content. */
    public static Text text(double heightPx, TextContent content) {
        return new Text(heightPx, content);
    }

    // --- Interactive Controls (HTML <button>, <input>, <progress>, <canvas>) -----------------------------

    /** Creates an interactive button with explicit font height. */
    public static Button button(Player viewer, double heightPx, String miniMessage, Consumer<Player> onClick) {
        return new Button(viewer, heightPx, miniMessage, onClick);
    }

    /** Creates an interactive button with dynamic text content and font height. */
    public static Button button(Player viewer, double heightPx, TextContent content, Consumer<Player> onClick) {
        return new Button(viewer, heightPx, content, onClick);
    }

    /** Creates an interactive button with default font height. */
    public static Button button(Player viewer, String miniMessage, Consumer<Player> onClick) {
        return new Button(viewer, 20, miniMessage, onClick);
    }

    /** Creates an interactive button with default session binding. */
    public static Button button(double heightPx, String miniMessage, Consumer<Player> onClick) {
        return new Button(heightPx, miniMessage, onClick);
    }

    /** Creates an interactive button with dynamic text content. */
    public static Button button(double heightPx, TextContent content, Consumer<Player> onClick) {
        return new Button(heightPx, content, onClick);
    }

    /** Creates a checkbox with a geometric indicator and optional label. */
    public static Checkbox checkbox(
        Player viewer,
        String label,
        boolean checked,
        BiConsumer<Player, Boolean> onChange
    ) {
        return new Checkbox(viewer, label, checked, onChange);
    }

    /** Creates a checkbox with a text label. */
    public static Checkbox checkbox(String label, boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Checkbox(label, checked, onChange);
    }

    /** Creates a checkbox without a text label. */
    public static Checkbox checkbox(boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Checkbox(checked, onChange);
    }

    /** Creates a toggle switch with optional text label. */
    public static Toggle toggle(Player viewer, String label, boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Toggle(viewer, label, checked, onChange);
    }

    /** Creates a toggle switch with a text label. */
    public static Toggle toggle(String label, boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Toggle(label, checked, onChange);
    }

    /** Creates a toggle switch without a text label. */
    public static Toggle toggle(boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Toggle(checked, onChange);
    }

    /** Creates a static progress bar with progress from 0.0 to 1.0. */
    public static ProgressBar progressBar(double progress) {
        return new ProgressBar(progress);
    }

    /** Creates a progress bar backed by a dynamic value supplier. */
    public static ProgressBar progressBar(DoubleSupplier progressSupplier) {
        return new ProgressBar(progressSupplier);
    }

    /** Creates an interactive drawing canvas for the viewing player. */
    public static Canvas canvas(Player viewer, int gridWidth, int gridHeight) {
        return new Canvas(viewer, gridWidth, gridHeight);
    }

    /** Creates an interactive drawing canvas. */
    public static Canvas canvas(int gridWidth, int gridHeight) {
        return new Canvas(gridWidth, gridHeight);
    }

    // --- 3D Models (Items & Blocks) ----------------------------------------------------------------------

    /** Creates a 3D item display node from a material. */
    public static Item item(Player viewer, Material material, double sizePx) {
        return new Item(viewer, material, sizePx);
    }

    /** Creates a 3D item display node from an item stack. */
    public static Item item(Player viewer, ItemStack stack, double sizePx) {
        return new Item(viewer, stack, sizePx);
    }

    /** Creates a 3D item display node from a dynamic item supplier. */
    public static Item item(Player viewer, Supplier<ItemStack> stack, double sizePx) {
        return new Item(viewer, stack, sizePx);
    }

    /** Creates a 3D item display node from a material. */
    public static Item item(Material material, double sizePx) {
        return new Item(material, sizePx);
    }

    /** Creates a 3D item display node from an item stack. */
    public static Item item(ItemStack stack, double sizePx) {
        return new Item(stack, sizePx);
    }

    /** Creates a 3D item display node from a dynamic item supplier. */
    public static Item item(Supplier<ItemStack> stack, double sizePx) {
        return new Item(stack, sizePx);
    }

    /** Creates a 3D block display node from a material. */
    public static Block block(Player viewer, Material material, double sizePx) {
        return new Block(viewer, material, sizePx);
    }

    /** Creates a 3D block display node with explicit dimensions. */
    public static Block block(Player viewer, Material material, double widthPx, double heightPx) {
        return new Block(viewer, material, widthPx, heightPx);
    }

    /** Creates a 3D block display node from block data. */
    public static Block block(Player viewer, BlockData block, double sizePx) {
        return new Block(viewer, block, sizePx);
    }

    /** Creates a 3D block display node with explicit dimensions. */
    public static Block block(Player viewer, BlockData block, double widthPx, double heightPx) {
        return new Block(viewer, block, widthPx, heightPx);
    }

    /** Creates a 3D block display node from a dynamic supplier. */
    public static Block block(Player viewer, Supplier<BlockData> block, double widthPx, double heightPx) {
        return new Block(viewer, block, widthPx, heightPx);
    }

    /** Creates a 3D block display node from a material. */
    public static Block block(Material material, double sizePx) {
        return new Block(material, sizePx);
    }

    /** Creates a 3D block display node with explicit dimensions. */
    public static Block block(Material material, double widthPx, double heightPx) {
        return new Block(material, widthPx, heightPx);
    }

    // --- Grid Tracks, Spacers & Transitions --------------------------------------------------------------

    /** Creates a fixed grid track in canvas pixels. */
    public static Track px(double size) {
        return new Track(Track.Kind.PX, size);
    }

    /** Creates a fractional grid track. */
    public static Track fr(double fraction) {
        return new Track(Track.Kind.FR, fraction);
    }

    /** Creates an automatically sized grid track. */
    public static Track auto() {
        return Track.AUTO;
    }

    /** Creates repeated copies of a grid track. */
    public static Track[] repeat(int count, Track track) {
        Track[] tracks = new Track[Math.max(0, count)];
        Arrays.fill(tracks, track);
        return tracks;
    }

    /** Creates a flexible spacer that fills available space. */
    public static Spacer spacer() {
        return new Spacer();
    }

    /** Creates a fixed spacer of specified pixel size. */
    public static Spacer space(double size) {
        return new Spacer(size);
    }

    /** Creates a transition with duration and easing function. */
    public static Transition transition(long durationMs, Easing easing) {
        return Transition.all(durationMs, easing);
    }

    /** Creates a transition with duration and default ease function. */
    public static Transition transition(long durationMs) {
        return Transition.all(durationMs, Easing.EASE);
    }

    /** Creates a transition for a specific property. */
    public static Transition transition(Transition.Property property, long durationMs, Easing easing) {
        return new Transition(property, durationMs, easing);
    }

    // --- Mounting ----------------------------------------------------------------------------------------

    /** Wraps a layout tree to mount on a canvas. */
    public static MountedLayout mount(LayoutItem root) {
        return new MountedLayout(box(root));
    }

    /** Wraps a layout tree bound to a viewing player. */
    public static MountedLayout mount(Player viewer, LayoutItem root) {
        return new MountedLayout(box(root)).viewer(viewer);
    }

    static Box<?> box(LayoutItem item) {
        if (item instanceof Box<?> box) {
            return box;
        }

        if (item instanceof AbstractCanvasComponent component) {
            return new Text(component);
        }

        if (item instanceof CanvasItem itemModel) {
            return new Item(itemModel);
        }

        if (item instanceof CanvasBlock blockModel) {
            return new Block(blockModel);
        }

        throw new IllegalArgumentException("Unsupported layout item: " + item);
    }
}
