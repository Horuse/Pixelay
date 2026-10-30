package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.content.TextWrap;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.VAlign;
import dev.horuse.pixelay.debug.Fit;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Defines geometry and dynamic content for a canvas element separate from rendering logic.
 * {@link CanvasRenderer} implementations use this data to spawn elements on the server or via packets.
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
     * @return TextDisplay line width in font pixels. The client wraps longer lines.
     *     Defaults to {@code text.line-width}. Layouts set this value to allocated width.
     */
    default int lineWidth() {
        return Pixelay.settings().text().lineWidth();
    }

    /** @return how the text wraps (CSS {@code white-space} / {@code word-break}) */
    default TextWrap textWrap() {
        return TextWrap.NORMAL;
    }

    /** @return depth of the text in front of the canvas surface, in blocks (see {@link Layers}) */
    default float textDepth() {
        return Layers.at(Layers.DEFAULT_TEXT_STEP);
    }

    /** @return depth of the text background in blocks, one step under the text */
    default float textBackgroundDepth() {
        return Layers.at(Layers.DEFAULT_TEXT_BACKGROUND_STEP);
    }

    /** @return whether the layout debug mode may tint this element's text background (the cursor opts out) */
    default boolean debugBackground() {
        return true;
    }

    /**
     * @return bounding clip rectangle in canvas pixels, or {@code null} to draw without clipping.
     *     The clip cuts content exceeding parent box overflow and canvas bounds.
     */
    default Rect clip() {
        return null;
    }

    /** @return whether the last layout pass found this element (or its box) bigger than its space */
    default Fit fit() {
        return Fit.NONE;
    }

    /**
     * @return version counter incremented on position, alignment, or hitbox changes.
     *     Renderers use this counter to reposition elements without text changes.
     */
    default int layoutVersion() {
        return 0;
    }

    /**
     * @return horizontal anchor edge for {@link #pixelX()}. Defaults to {@link HAlign#CENTER}.
     */
    default HAlign hAlign() {
        return HAlign.CENTER;
    }

    /**
     * @return vertical anchor edge for {@link #pixelY()}. Defaults to {@link VAlign#BOTTOM}.
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

    /** Visual scale multiplier on the X axis (from CSS {@code transform: scaleX(...)}), defaults to {@code 1.0}. */
    default double visualScaleX() {
        return 1.0;
    }

    /** Visual scale multiplier on the Y axis (from CSS {@code transform: scaleY(...)}), defaults to {@code 1.0}. */
    default double visualScaleY() {
        return 1.0;
    }

    /** Visual rotation around the canvas Z normal in degrees (clockwise, CSS {@code transform: rotate(deg)}). */
    default double rotateDeg() {
        return 0.0;
    }

    /** Visual 3D rotation around the X axis in degrees (CSS {@code transform: rotateX(deg)}). */
    default double rotateXDeg() {
        return 0.0;
    }

    /** Visual 3D rotation around the Y axis in degrees (CSS {@code transform: rotateY(deg)}). */
    default double rotateYDeg() {
        return 0.0;
    }

    /** Duration in game ticks for client-side GPU transformation interpolation. */
    default int transformationDuration() {
        return 0;
    }

    /** Pivot X in canvas px around which {@link #rotateDeg()} revolves (or {@code Double.NaN} for self center). */
    default double rotatePivotX() {
        return Double.NaN;
    }

    /** Pivot Y in canvas px around which {@link #rotateDeg()} revolves (or {@code Double.NaN} for self center). */
    default double rotatePivotY() {
        return Double.NaN;
    }
}
