package dev.horuse.pixelay.components;

import dev.horuse.pixelay.layout.LayoutItem;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

/** A canvas component a layout can hold: text ({@link AbstractCanvasComponent}) or a model ({@link CanvasModel}). */
public interface LayoutComponent extends CanvasComponent, LayoutItem {
    /** @return the only player who sees this element, or {@code null} when everyone does */
    Player viewer();

    /** Set by layouts: the first depth step of this element, see {@link dev.horuse.pixelay.data.Layers}. */
    @ApiStatus.Internal
    void setLayerStep(int step);

    /** Set by layouts from their pointer, see {@code MountedLayout.pointerAt}. */
    @ApiStatus.Internal
    void setHovered(boolean hovered);
}
