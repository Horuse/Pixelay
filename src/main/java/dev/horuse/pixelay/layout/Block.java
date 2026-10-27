package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.CanvasBlock;
import dev.horuse.pixelay.components.LayoutComponent;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * A 3D block model element inside a layout (like an HTML {@code <img>} for Minecraft blocks).
 * Keeps its natural {@code widthPx x heightPx} size, pinned inside its rectangle by {@link #justifySelf}
 * and {@link #alignSelf} (top-left by default); {@link Align#STRETCH} makes it fill that axis.
 */
public final class Block extends Box<Block> {

    private final CanvasBlock model;

    public Block(CanvasBlock model) {
        this.model = model;
    }

    public Block(Player viewer, Supplier<BlockData> block, double widthPx, double heightPx) {
        this(
            viewer != null
                ? CanvasBlock.fake(viewer, 0, 0, widthPx, heightPx, block)
                : new CanvasBlock(0, 0, widthPx, heightPx, block)
        );
    }

    public Block(Player viewer, BlockData block, double widthPx, double heightPx) {
        this(viewer, () -> block, widthPx, heightPx);
    }

    public Block(Player viewer, BlockData block, double sizePx) {
        this(viewer, () -> block, sizePx, sizePx);
    }

    public Block(Player viewer, Material material, double widthPx, double heightPx) {
        this(viewer, material.createBlockData(), widthPx, heightPx);
    }

    public Block(Player viewer, Material material, double sizePx) {
        this(viewer, material.createBlockData(), sizePx, sizePx);
    }

    public Block(Supplier<BlockData> block, double widthPx, double heightPx) {
        this(null, block, widthPx, heightPx);
    }

    public Block(BlockData block, double widthPx, double heightPx) {
        this(null, () -> block, widthPx, heightPx);
    }

    public Block(BlockData block, double sizePx) {
        this(null, () -> block, sizePx, sizePx);
    }

    public Block(Material material, double widthPx, double heightPx) {
        this(null, material.createBlockData(), widthPx, heightPx);
    }

    public Block(Material material, double sizePx) {
        this(null, material.createBlockData(), sizePx, sizePx);
    }

    @Override
    public String tagName() {
        return "block";
    }

    @Override
    public CanvasBlock component() {
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
