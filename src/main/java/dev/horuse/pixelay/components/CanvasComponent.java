package dev.horuse.pixelay.components;

import dev.horuse.pixelay.data.Canvas;

/** Base interface for components rendered on a {@link Canvas}. */
public interface CanvasComponent {
    /** Spawns component entities on the canvas. */
    void spawn(Canvas canvas);

    /** Removes component entities from the world. */
    void despawn();

    /**
     * @return true if all backing entities are valid, false otherwise
     */
    boolean isValid();

    /** Re-evaluates dynamic content and updates entities in place. */
    void refresh();

    /** Sets the client-side GPU transformation interpolation duration in game ticks (for 140+ FPS transitions). */
    default void setTransformationDuration(int ticks) {}
}
