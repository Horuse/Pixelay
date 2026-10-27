package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.CanvasItem;
import dev.horuse.pixelay.components.LayoutComponent;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A 3D item model element inside a layout (like an HTML {@code <img>} for Minecraft items).
 * Keeps its natural square size {@code sizePx}, pinned inside its rectangle by {@link #justifySelf}
 * and {@link #alignSelf} (top-left by default); {@link Align#STRETCH} makes it fill that axis.
 */
public final class Item extends Box<Item> {

    private final CanvasItem model;

    public Item(CanvasItem model) {
        this.model = model;
    }

    public Item(Player viewer, Supplier<ItemStack> stack, double sizePx) {
        this(viewer != null ? CanvasItem.fake(viewer, 0, 0, sizePx, stack) : new CanvasItem(0, 0, sizePx, stack));
    }

    public Item(Player viewer, ItemStack stack, double sizePx) {
        this(viewer, () -> stack, sizePx);
    }

    public Item(Player viewer, Material material, double sizePx) {
        this(viewer, new ItemStack(material), sizePx);
    }

    public Item(Supplier<ItemStack> stack, double sizePx) {
        this(null, stack, sizePx);
    }

    public Item(ItemStack stack, double sizePx) {
        this(null, () -> stack, sizePx);
    }

    public Item(Material material, double sizePx) {
        this(null, new ItemStack(material), sizePx);
    }

    @Override
    public String tagName() {
        return "item";
    }

    @Override
    public CanvasItem component() {
        return model;
    }

    @Override
    void bindViewer(Player viewer) {
        model.bindViewer(viewer);
    }

    @Override
    protected Size measureContent(double maxWidth) {
        return new Size(model.naturalWidthPx(), model.naturalHeightPx());
    }

    @Override
    protected double minContentWidth() {
        return model.naturalWidthPx();
    }

    @Override
    protected void arrangeContent(Rect content) {
        Align horizontal = justifySelf != null ? justifySelf : Align.START;
        Align vertical = alignSelf != null ? alignSelf : Align.START;
        Rect box = Align.place(
            content,
            new Size(model.naturalWidthPx() * visualScaleX(), model.naturalHeightPx() * visualScaleY()),
            horizontal,
            vertical,
            horizontal == Align.STRETCH,
            vertical == Align.STRETCH
        );
        model.setVisualRotation(visualRotateDeg(), visualRotateXDeg(), visualRotateYDeg());
        model.place(box.x(), box.y(), box.width(), box.height());
    }

    @Override
    protected void collectComponents(List<LayoutComponent> out) {
        out.add(model);
    }
}
