package dev.horuse.pixelay.screen;

import java.util.Objects;

/**
 * Defines a screen transition effect when opening or closing a {@link ScreenSession}.
 */
public final class ScreenTransition {

    public enum Type {
        NONE,
        FADE,
        CIRCLE_WIPE,
        LINEAR_WIPE,
        DISSOLVE,
    }

    private final Type type;
    private final long durationMs;
    private final int overlayColor;

    public ScreenTransition(Type type, long durationMs, int overlayColor) {
        this.type = Objects.requireNonNull(type, "type");
        this.durationMs = Math.max(0, durationMs);
        this.overlayColor = overlayColor;
    }

    public static ScreenTransition none() {
        return new ScreenTransition(Type.NONE, 0, 0);
    }

    public static ScreenTransition fade(long durationMs) {
        return new ScreenTransition(Type.FADE, durationMs, 0xFF000000);
    }

    public static ScreenTransition fade(long durationMs, int overlayColor) {
        return new ScreenTransition(Type.FADE, durationMs, overlayColor);
    }

    public static ScreenTransition circleWipe(long durationMs) {
        return new ScreenTransition(Type.CIRCLE_WIPE, durationMs, 0xFF000000);
    }

    public static ScreenTransition circleWipe(long durationMs, int overlayColor) {
        return new ScreenTransition(Type.CIRCLE_WIPE, durationMs, overlayColor);
    }

    public static ScreenTransition linearWipe(long durationMs) {
        return new ScreenTransition(Type.LINEAR_WIPE, durationMs, 0xFF000000);
    }

    public static ScreenTransition linearWipe(long durationMs, int overlayColor) {
        return new ScreenTransition(Type.LINEAR_WIPE, durationMs, overlayColor);
    }

    public static ScreenTransition dissolve(long durationMs) {
        return new ScreenTransition(Type.DISSOLVE, durationMs, 0xFF000000);
    }

    public static ScreenTransition dissolve(long durationMs, int overlayColor) {
        return new ScreenTransition(Type.DISSOLVE, durationMs, overlayColor);
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
}
