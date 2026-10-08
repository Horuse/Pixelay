package dev.horuse.pixelay.components;

import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.VAlign;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The geometry and dynamic content of a canvas element, decoupled from how it is actually drawn.
 * Renderers ({@link CanvasRenderer}) consume this so the same element can be spawned server-side or
 * per-player via packets without duplicating layout logic.
 */
public interface CanvasDrawable {
    /** Horizontal pixel coordinate of the anchor point. */
    double pixelX();

    /** Vertical pixel coordinate of the anchor point. */
    double pixelY();

    /** Pixel height used to size the element on the canvas. */
    double heightPx();

    /** Current rendered text, re-evaluated on refresh. */
    String renderText();

    /**
     * @return horizontal anchor: which edge of the element's box {@link #pixelX()} pins (defaults
     *     to CENTER — the historical behavior)
     */
    default HAlign hAlign() {
        return HAlign.CENTER;
    }

    /**
     * @return vertical anchor: which edge of the element's box {@link #pixelY()} pins (defaults to
     *     BOTTOM — the historical behavior, text grows upward)
     */
    default VAlign vAlign() {
        return VAlign.BOTTOM;
    }

    /**
     * @return explicit hitbox width in canvas px, or {@code <= 0} for auto (measured text width)
     */
    default double hitboxWidthPx() {
        return -1;
    }

    /**
     * @return explicit hitbox height in canvas px, or {@code <= 0} for auto (text block height)
     */
    default double hitboxHeightPx() {
        return -1;
    }

    /** Click handler if this element is clickable, otherwise null. */
    @Nullable
    Consumer<Player> onClick();
}
