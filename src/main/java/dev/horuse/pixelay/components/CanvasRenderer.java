package dev.horuse.pixelay.components;

import dev.horuse.pixelay.data.Canvas;

/**
 * Strategy that knows how a {@link CanvasComponent} is drawn into the world. A component keeps its
 * geometry/content ({@link CanvasDrawable}) while a renderer owns the backing entities, so the same
 * element can be spawned either as ordinary server entities visible to everyone ({@link
 * ServerCanvasRenderer}) or rendered per-player via packets ({@link PacketCanvasRenderer}).
 */
public interface CanvasRenderer {
    /** Spawns the renderer's backing entities, positioned via the canvas. */
    void spawn(Canvas canvas);

    /** Removes the renderer's backing entities from the world. */
    void despawn();

    /**
     * @return whether every backing entity is still alive and valid
     */
    boolean isValid();

    /** Re-evaluates dynamic content and updates the backing entities in place. */
    void refresh();

    /** Repositions the backing entity to a world position (no-op for non-movable renderers). */
    default void move(org.bukkit.util.Vector worldPos) {}

    /**
     * @return measured hitbox of the last rendered text {@code {width, height}} in canvas px, or
     *     null before the first spawn; used for click hit-testing
     */
    default double[] measuredHitboxPx() {
        return null;
    }

    /** @return the only player who sees this element, or {@code null} when everyone does */
    default org.bukkit.entity.Player viewer() {
        return null;
    }
}
