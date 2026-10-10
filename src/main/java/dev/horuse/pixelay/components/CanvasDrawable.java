package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.VAlign;
import dev.horuse.pixelay.debug.Overflow;
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
     * @return TextDisplay line width in font px: the client wraps longer lines. Defaults to the
     *     configured {@code text.line-width}; layouts lower it to the width they give the element.
     */
    default int lineWidth() {
        return Pixelay.settings().text().lineWidth();
    }

    /** @return whether the layout debug mode may tint this element's text background (the cursor opts out) */
    default boolean debugBackground() {
        return true;
    }

    /** @return whether the last layout pass found this element (or its box) bigger than its space */
    default Overflow overflow() {
        return Overflow.NONE;
    }

    /**
     * @return a counter bumped whenever position, alignment or hitbox change, so renderers reposition
     *     the element even when its text did not change (e.g. after a layout pass)
     */
    default int layoutVersion() {
        return 0;
    }

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
