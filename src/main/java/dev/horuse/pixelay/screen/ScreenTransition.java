package dev.horuse.pixelay.screen;

import dev.horuse.pixelay.layout.Easing;
import java.util.Objects;

/**
 * Defines a screen transition effect when opening or closing a {@link ScreenSession}.
 * Supports GPU hardware shader transitions (Fade, Circle Wipe/Iris, Directional Linear Wipes,
 * Dissolve, Curtain/Split) with customizable durations, overlay colors, and easing curves.
 */
public final class ScreenTransition {

    public enum Type {
        NONE(-1),
        FADE(0),
        CIRCLE_WIPE(1),
        LINEAR_WIPE_LEFT(2),
        LINEAR_WIPE_RIGHT(3),
        LINEAR_WIPE_DOWN(4),
        LINEAR_WIPE_UP(5),
        DISSOLVE(6),
        CURTAIN(7);

        private final int shaderCode;

        Type(int shaderCode) {
            this.shaderCode = shaderCode;
        }

        public int shaderCode() {
            return shaderCode;
        }
    }

    public static final Type LINEAR_WIPE = Type.LINEAR_WIPE_RIGHT;

    private final Type type;
    private final long durationMs;
    private final int overlayColor;
    private final Easing easing;

    public ScreenTransition(Type type, long durationMs, int overlayColor, Easing easing) {
        this.type = Objects.requireNonNull(type, "type");
        this.durationMs = Math.max(0, durationMs);
        this.overlayColor = overlayColor;
        this.easing = easing != null ? easing : Easing.EASE_IN_OUT;
    }

    public ScreenTransition(Type type, long durationMs, int overlayColor) {
        this(type, durationMs, overlayColor, Easing.EASE_IN_OUT);
    }

    public static ScreenTransition none() {
        return new ScreenTransition(Type.NONE, 0, 0, Easing.LINEAR);
    }

    public static ScreenTransition fade(long durationMs) {
        return new ScreenTransition(Type.FADE, durationMs, 0xFF000000, Easing.EASE_IN_OUT);
    }

    public static ScreenTransition fade(long durationMs, int overlayColor) {
        return new ScreenTransition(Type.FADE, durationMs, overlayColor, Easing.EASE_IN_OUT);
    }

    public static ScreenTransition fade(long durationMs, int overlayColor, Easing easing) {
        return new ScreenTransition(Type.FADE, durationMs, overlayColor, easing);
    }

    public static ScreenTransition circleWipe(long durationMs) {
        return new ScreenTransition(Type.CIRCLE_WIPE, durationMs, 0xFF000000, Easing.EASE_OUT);
    }

    public static ScreenTransition circleWipe(long durationMs, int overlayColor) {
        return new ScreenTransition(Type.CIRCLE_WIPE, durationMs, overlayColor, Easing.EASE_OUT);
    }

    public static ScreenTransition circleWipe(long durationMs, int overlayColor, Easing easing) {
        return new ScreenTransition(Type.CIRCLE_WIPE, durationMs, overlayColor, easing);
    }

    public static ScreenTransition linearWipe(long durationMs) {
        return new ScreenTransition(Type.LINEAR_WIPE_RIGHT, durationMs, 0xFF000000, Easing.EASE_IN_OUT);
    }

    public static ScreenTransition linearWipe(long durationMs, int overlayColor) {
        return new ScreenTransition(Type.LINEAR_WIPE_RIGHT, durationMs, overlayColor, Easing.EASE_IN_OUT);
    }

    public static ScreenTransition linearWipe(long durationMs, Type direction, int overlayColor, Easing easing) {
        return new ScreenTransition(
            direction != null ? direction : Type.LINEAR_WIPE_RIGHT,
            durationMs,
            overlayColor,
            easing
        );
    }

    public static ScreenTransition dissolve(long durationMs) {
        return new ScreenTransition(Type.DISSOLVE, durationMs, 0xFF000000, Easing.LINEAR);
    }

    public static ScreenTransition dissolve(long durationMs, int overlayColor) {
        return new ScreenTransition(Type.DISSOLVE, durationMs, overlayColor, Easing.LINEAR);
    }

    public static ScreenTransition dissolve(long durationMs, int overlayColor, Easing easing) {
        return new ScreenTransition(Type.DISSOLVE, durationMs, overlayColor, easing);
    }

    public static ScreenTransition curtain(long durationMs) {
        return new ScreenTransition(Type.CURTAIN, durationMs, 0xFF000000, Easing.EASE_IN_OUT);
    }

    public static ScreenTransition curtain(long durationMs, int overlayColor) {
        return new ScreenTransition(Type.CURTAIN, durationMs, overlayColor, Easing.EASE_IN_OUT);
    }

    public static ScreenTransition curtain(long durationMs, int overlayColor, Easing easing) {
        return new ScreenTransition(Type.CURTAIN, durationMs, overlayColor, easing);
    }

    public ScreenTransition easing(Easing easing) {
        return new ScreenTransition(this.type, this.durationMs, this.overlayColor, easing);
    }

    public ScreenTransition color(int overlayColor) {
        return new ScreenTransition(this.type, this.durationMs, overlayColor, this.easing);
    }

    public ScreenTransition duration(long durationMs) {
        return new ScreenTransition(this.type, durationMs, this.overlayColor, this.easing);
    }

    public ScreenTransition type(Type type) {
        return new ScreenTransition(type, this.durationMs, this.overlayColor, this.easing);
    }

    public Type type() {
        return type;
    }

    public long durationMs() {
        return durationMs;
    }

    public int overlayColor() {
        return overlayColor;
    }

    public Easing easing() {
        return easing;
    }
}
