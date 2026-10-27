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
 * Static factory helpers and CSS unit builders ({@link #px}, {@link #fr}, {@link #auto}, {@link #repeat})
 * for the HTML/CSS-like layout engine.
 * <p>
 * Every element is also a first-class Java class that can be instantiated directly with {@code new}:
 * {@link Box}, {@link Row}, {@link Column}, {@link Flex}, {@link Grid}, {@link Stack}, {@link ScrollView},
 * {@link Text}, {@link Button}, {@link Checkbox}, {@link Toggle}, {@link ProgressBar}, {@link Canvas},
 * {@link Item}, {@link Block}, and {@link Spacer}.
 */
public final class Layout {

    private Layout() {}

    // --- Containers (HTML <div>, Flexbox, Grid, Stack, ScrollView) ---------------------------------------

    /** An empty block box (like an empty HTML {@code <div>}), useful for panels, dividers, and shapes. */
    public static Box<?> box() {
        return new Box<>();
    }

    /** A vertical block container (HTML {@code <div>} / CSS {@code display: flex; flex-direction: column}). */
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

    /** A scrollable vertical container with an optional scrollbar. */
    public static ScrollView scrollView(LayoutItem... children) {
        return new ScrollView(children);
    }

    /** A scrollable container wrapping {@code content}. */
    public static ScrollView scrollView(Box<?> content) {
        return new ScrollView(content);
    }

    // --- Text Nodes (HTML <p> / <span>) ------------------------------------------------------------------

    /** Creates a text node for {@code viewer} (or a public server text when {@code viewer == null}). */
    public static Text text(Player viewer, double heightPx, String miniMessage) {
        return new Text(viewer, heightPx, miniMessage);
    }

    /** Creates a text node with custom or dynamic {@link TextContent} (e.g. {@code () -> "..."}). */
    public static Text text(Player viewer, double heightPx, TextContent content) {
        return new Text(viewer, heightPx, content);
    }

    /** Creates a text node (auto-binds to session viewer when inside a {@code ScreenSession}, or public on world canvases). */
    public static Text text(double heightPx, String miniMessage) {
        return new Text(heightPx, miniMessage);
    }

    /** Creates a text node with custom or dynamic {@link TextContent}. */
    public static Text text(double heightPx, TextContent content) {
        return new Text(heightPx, content);
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

    /** Creates an interactive {@link Button} (auto-binds to session viewer when inside a {@code ScreenSession}). */
    public static Button button(double heightPx, String miniMessage, Consumer<Player> onClick) {
        return new Button(heightPx, miniMessage, onClick);
    }

    /** Creates an interactive {@link Button} with dynamic {@link TextContent}. */
    public static Button button(double heightPx, TextContent content, Consumer<Player> onClick) {
        return new Button(heightPx, content, onClick);
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

    /** Creates an interactive {@link Checkbox} with a text label. */
    public static Checkbox checkbox(String label, boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Checkbox(label, checked, onChange);
    }

    /** Creates an interactive {@link Checkbox} without a text label. */
    public static Checkbox checkbox(boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Checkbox(checked, onChange);
    }

    /** Creates an interactive pill {@link Toggle} switch with a sliding thumb and optional text label. */
    public static Toggle toggle(Player viewer, String label, boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Toggle(viewer, label, checked, onChange);
    }

    /** Creates an interactive pill {@link Toggle} switch with a text label. */
    public static Toggle toggle(String label, boolean checked, BiConsumer<Player, Boolean> onChange) {
        return new Toggle(label, checked, onChange);
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

    /** Creates an HTML {@code <canvas>}-like 2D pixel grid and interactive drawing surface. */
    public static Canvas canvas(int gridWidth, int gridHeight) {
        return new Canvas(gridWidth, gridHeight);
    }

    // --- 3D Models (Items & Blocks) ----------------------------------------------------------------------

    /** Creates a 3D item display node inside a layout from a {@link Material}. */
    public static Item item(Player viewer, Material material, double sizePx) {
        return new Item(viewer, material, sizePx);
    }

    /** Creates a 3D item display node inside a layout. */
    public static Item item(Player viewer, ItemStack stack, double sizePx) {
        return new Item(viewer, stack, sizePx);
    }

    /** Creates a dynamic 3D item display node inside a layout. */
    public static Item item(Player viewer, Supplier<ItemStack> stack, double sizePx) {
        return new Item(viewer, stack, sizePx);
    }

    /** Creates a 3D item display node inside a layout from a {@link Material}. */
    public static Item item(Material material, double sizePx) {
        return new Item(material, sizePx);
    }

    /** Creates a 3D item display node inside a layout. */
    public static Item item(ItemStack stack, double sizePx) {
        return new Item(stack, sizePx);
    }

    /** Creates a dynamic 3D item display node inside a layout. */
    public static Item item(Supplier<ItemStack> stack, double sizePx) {
        return new Item(stack, sizePx);
    }

    /** Creates a 3D block display node inside a layout from a {@link Material}. */
    public static Block block(Player viewer, Material material, double sizePx) {
        return new Block(viewer, material, sizePx);
    }

    /** Creates a 3D block display node with explicit width and height inside a layout from a {@link Material}. */
    public static Block block(Player viewer, Material material, double widthPx, double heightPx) {
        return new Block(viewer, material, widthPx, heightPx);
    }

    /** Creates a 3D block display node inside a layout. */
    public static Block block(Player viewer, BlockData block, double sizePx) {
        return new Block(viewer, block, sizePx);
    }

    /** Creates a 3D block display node with explicit width and height inside a layout. */
    public static Block block(Player viewer, BlockData block, double widthPx, double heightPx) {
        return new Block(viewer, block, widthPx, heightPx);
    }

    /** Creates a dynamic 3D block display node with explicit width and height inside a layout. */
    public static Block block(Player viewer, Supplier<BlockData> block, double widthPx, double heightPx) {
        return new Block(viewer, block, widthPx, heightPx);
    }

    /** Creates a 3D block display node inside a layout from a {@link Material}. */
    public static Block block(Material material, double sizePx) {
        return new Block(material, sizePx);
    }

    /** Creates a 3D block display node with explicit width and height inside a layout from a {@link Material}. */
    public static Block block(Material material, double widthPx, double heightPx) {
        return new Block(material, widthPx, heightPx);
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
        return new Spacer();
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

    // --- Mounting ----------------------------------------------------------------------------------------

    /** Wraps a layout tree for spawning on a canvas. Note: {@link Box} also implements {@code CanvasComponent} directly. */
    public static MountedLayout mount(LayoutItem root) {
        return new MountedLayout(box(root));
    }

    /** Wraps a layout tree bound to {@code viewer} for spawning on a canvas. */
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
