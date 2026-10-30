package dev.horuse.pixelay;

/**
 * A live surface that {@link CanvasManager} manages: a canvas and its components. Consumers
 * implement this interface and register so the manager ticks refresh and self-healing centrally.
 */
public interface CanvasSurface {
    /** Re-evaluates dynamic content of all components. */
    void refresh();

    /**
     * @return true if every backing entity is alive, false otherwise
     */
    boolean isValid();

    /** Despawns and spawns components again when self-healing detects a dead entity. */
    void respawn();
}
