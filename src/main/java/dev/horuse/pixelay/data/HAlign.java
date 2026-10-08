package dev.horuse.pixelay.data;

import org.bukkit.entity.TextDisplay;

/**
 * Horizontal anchor of a component box relative to its {@code pixelX}: the anchor point pins the
 * LEFT/CENTER/RIGHT edge of the box, like CSS {@code left/center/right}. A TextDisplay box is
 * always centered on its entity position, so the anchoring is implemented by shifting the spawn
 * point with {@link #horizontalOffset} using the measured text width ({@link
 * dev.horuse.pixelay.content.FontMetrics}); the native TextDisplay alignment only presses the lines
 * of a multi-line block to the same edge.
 */
public enum HAlign {
    LEFT,
    CENTER,
    RIGHT;

    /**
     * @return offset (canvas px) from the anchor point to the box center, so the anchored edge of a
     *     box of the given width lands exactly on the anchor
     */
    public double horizontalOffset(double boxWidthPx) {
        return switch (this) {
            case LEFT -> boxWidthPx / 2;
            case CENTER -> 0;
            case RIGHT -> -boxWidthPx / 2;
        };
    }

    /**
     * @return the native TextDisplay alignment that presses multi-line block's lines to the same
     *     edge
     */
    public TextDisplay.TextAlignment textAlignment() {
        return switch (this) {
            case LEFT -> TextDisplay.TextAlignment.LEFT;
            case RIGHT -> TextDisplay.TextAlignment.RIGHT;
            case CENTER -> TextDisplay.TextAlignment.CENTER;
        };
    }
}
