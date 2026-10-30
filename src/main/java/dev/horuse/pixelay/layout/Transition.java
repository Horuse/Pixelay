package dev.horuse.pixelay.layout;

import java.util.Objects;

/**
 * Defines CSS property transition settings.
 */
public final class Transition {

    public enum Property {
        ALL,
        BACKGROUND,
        TRANSFORM,
    }

    private final Property property;
    private final long durationMs;
    private final Easing easing;

    public Transition(Property property, long durationMs, Easing easing) {
        this.property = Objects.requireNonNull(property, "property");
        this.durationMs = Math.max(0, durationMs);
        this.easing = Objects.requireNonNullElse(easing, Easing.EASE);
    }

    public static Transition background(long durationMs, Easing easing) {
        return new Transition(Property.BACKGROUND, durationMs, easing);
    }

    public static Transition background(long durationMs) {
        return new Transition(Property.BACKGROUND, durationMs, Easing.EASE);
    }

    public static Transition transform(long durationMs, Easing easing) {
        return new Transition(Property.TRANSFORM, durationMs, easing);
    }

    public static Transition transform(long durationMs) {
        return new Transition(Property.TRANSFORM, durationMs, Easing.EASE);
    }

    public static Transition all(long durationMs, Easing easing) {
        return new Transition(Property.ALL, durationMs, easing);
    }

    public static Transition all(long durationMs) {
        return new Transition(Property.ALL, durationMs, Easing.EASE);
    }

    public Property property() {
        return property;
    }

    public long durationMs() {
        return durationMs;
    }

    public Easing easing() {
        return easing;
    }
}
