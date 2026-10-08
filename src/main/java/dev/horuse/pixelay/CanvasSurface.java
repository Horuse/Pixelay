package dev.horuse.pixelay;

/**
 * A live drawing managed by the {@link CanvasManager}: a canvas plus its components. Consumers
 * implement this and register themselves so refresh and self-healing are ticked centrally.
 */
public interface CanvasSurface {
    /** Re-evaluate dynamic content of all components. */
    void refresh();

    /**
     * @return whether every backing entity is still alive
     */
    boolean isValid();

    /** Despawn and spawn everything again (used when self-heal detects a dead entity). */
    void respawn();
}
