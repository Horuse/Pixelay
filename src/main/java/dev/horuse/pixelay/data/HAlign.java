package dev.horuse.pixelay.data;

import org.bukkit.entity.TextDisplay;

/**
 * Specifies horizontal anchor alignment of a component box.
 * The anchor point aligns with the left, center, or right edge of the box.
 * The system calculates horizontal offset using measured text width.
 */
public enum HAlign {
    LEFT,
    CENTER,
    RIGHT;

    /**
     * @return horizontal pixel offset from anchor to box center
     */
    public double horizontalOffset(double boxWidthPx) {
        return switch (this) {
            case LEFT -> boxWidthPx / 2;
            case CENTER -> 0;
            case RIGHT -> -boxWidthPx / 2;
        };
    }

    /**
     * @return the corresponding Minecraft TextDisplay alignment
     */
    public TextDisplay.TextAlignment textAlignment() {
        return switch (this) {
            case LEFT -> TextDisplay.TextAlignment.LEFT;
            case RIGHT -> TextDisplay.TextAlignment.RIGHT;
            case CENTER -> TextDisplay.TextAlignment.CENTER;
        };
    }
}
