package dev.horuse.pixelay.components;

import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.layout.LayoutItem;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

/** Represents a canvas component within a layout, such as text ({@link AbstractCanvasComponent}) or a model ({@link CanvasModel}). */
public interface LayoutComponent extends CanvasComponent, LayoutItem {
    /** @return the player who sees this element, or {@code null} for all players */
    Player viewer();

    /** Sets the initial depth step of this element in painting order. */
    @ApiStatus.Internal
    void setLayerStep(int step);

    /** Sets hovered state from layout pointer events. */
    @ApiStatus.Internal
    void setHovered(boolean hovered);

    /**
     * Sets the bounding clip rectangle in canvas pixels.
     * Content outside this rectangle is clipped.
     */
    @ApiStatus.Internal
    void setClip(Rect clip);
}
