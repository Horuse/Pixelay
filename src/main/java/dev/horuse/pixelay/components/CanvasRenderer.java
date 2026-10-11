package dev.horuse.pixelay.components;

import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.Size;
import org.bukkit.entity.Player;

/**
 * Strategy that knows how a {@link CanvasComponent} is drawn into the world. A component keeps its
 * geometry/content ({@link CanvasDrawable}) while a renderer owns the backing entities, so the same
 * element can be drawn with real entities ({@link EntityRenderer}, for everyone or one player) or as
 * packet-only entities of one player ({@link FakeRenderer}).
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

    /**
     * @return the element's box size in canvas px from the last render, or null before the
     *     first spawn; used for click hit-testing
     */
    Size measuredHitboxPx();

    /** @return the only player who sees this element, or {@code null} when everyone does */
    Player viewer();
}
