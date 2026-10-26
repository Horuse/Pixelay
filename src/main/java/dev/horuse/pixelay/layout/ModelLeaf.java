package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.CanvasModel;
import dev.horuse.pixelay.components.LayoutComponent;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import java.util.List;

/**
 * An item or a block inside a layout. Like a CSS image it keeps its own size, pinned inside its rectangle by
 * {@link #justifySelf} and {@link #alignSelf} (top-left by default); {@code STRETCH} makes it fill that axis.
 */
public final class ModelLeaf extends Box<ModelLeaf> {

    private final CanvasModel<?> model;

    ModelLeaf(CanvasModel<?> model) {
        this.model = model;
    }

    @Override
    public String tagName() {
        return "model";
    }

    @Override
    public CanvasModel<?> component() {
        return model;
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
            new Size(model.naturalWidthPx(), model.naturalHeightPx()),
            horizontal,
            vertical,
            horizontal == Align.STRETCH,
            vertical == Align.STRETCH
        );
        model.place(box.x(), box.y(), box.width(), box.height());
    }

    @Override
    protected void collectComponents(List<LayoutComponent> out) {
        out.add(model);
    }
}
