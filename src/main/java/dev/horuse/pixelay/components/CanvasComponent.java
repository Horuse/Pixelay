package dev.horuse.pixelay.components;

import dev.horuse.pixelay.data.Canvas;

/** Anything drawn on a {@link Canvas}: text, buttons, icons, … */
public interface CanvasComponent {
    /** Spawns the component's entities positioned via the canvas. */
    void spawn(Canvas canvas);

    /** Removes the component's entities from the world. */
    void despawn();

    /**
     * @return whether all backing entities are alive and valid
     */
    boolean isValid();

    /** Re-evaluates dynamic content and updates the entities in place. */
    void refresh();

    /** Sets the client-side GPU transformation interpolation duration in game ticks (for 140+ FPS transitions). */
    default void setTransformationDuration(int ticks) {}
}
