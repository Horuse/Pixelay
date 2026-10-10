package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.components.CanvasComponent;
import dev.horuse.pixelay.components.CanvasLayout;
import dev.horuse.pixelay.data.Canvas;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * A layout tree attached to a canvas. It is itself a {@link CanvasComponent}: spawning lays the tree
 * out over the whole canvas and spawns every component; refreshing re-runs the layout (texts may have
 * changed size) and moves components that shifted.
 */
public final class MountedLayout implements CanvasComponent {

    // spawn/refresh/despawn must run on the main server thread: the debug overlay uses the Bukkit API.

    private final Box<?> root;
    private final List<AbstractCanvasComponent> components = new ArrayList<>();
    private final List<Box<?>> boxes = new ArrayList<>();
    private Canvas canvas;
    private final DebugOverlay debugOverlay = new DebugOverlay();

    MountedLayout(Box<?> root) {
        this.root = root;
        root.collectComponents(components);
        root.collectBoxes(boxes);
    }

    public Box<?> root() {
        return root;
    }

    public List<AbstractCanvasComponent> components() {
        return List.copyOf(components);
    }

    /** Runs measure + arrange over the whole canvas. */
    public void layout(Canvas canvas) {
        this.canvas = canvas;
        // Texts may have changed since the last pass: measure everything once more, then reuse.
        root.invalidate();
        root.arrange(new Rect(0, 0, canvas.resolution().pixelWidth(), canvas.resolution().pixelHeight()));
    }

    @Override
    public void spawn(Canvas canvas) {
        layout(canvas);
        components.forEach(component -> component.spawn(canvas));
        debugOverlay.update(root, components, canvas);
    }

    @Override
    public void despawn() {
        components.forEach(CanvasComponent::despawn);
        debugOverlay.clear();
    }

    @Override
    public boolean isValid() {
        return !components.isEmpty() && components.stream().allMatch(CanvasComponent::isValid);
    }

    @Override
    public void refresh() {
        if (canvas != null) {
            layout(canvas);
        }
        components.forEach(CanvasComponent::refresh);
        if (canvas != null) {
            debugOverlay.update(root, components, canvas);
        }
    }

    /**
     * Routes a click at a canvas pixel: the deepest box with {@code onClick} under the point wins,
     * otherwise a clickable component whose measured hitbox contains the point.
     *
     * @return whether anything handled the click
     */
    public boolean click(double px, double py, Player player) {
        px = clampToCanvas(px, true);
        py = clampToCanvas(py, false);
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box<?> box = boxes.get(i);
            if (box.onClick() != null && box.rect().contains(px, py)) {
                box.onClick().accept(player);
                return true;
            }
        }

        for (int i = components.size() - 1; i >= 0; i--) {
            AbstractCanvasComponent component = components.get(i);
            Consumer<Player> onClick = component.onClick();
            double[] hitbox = component.measuredHitboxPx();
            if (onClick != null && hitbox != null && CanvasLayout.contains(component, hitbox, px, py)) {
                onClick.accept(player);
                return true;
            }
        }
        return false;
    }

    /** @return the id of the deepest box with an id under the point, or {@code null} */
    public String idAt(double px, double py) {
        px = clampToCanvas(px, true);
        py = clampToCanvas(py, false);
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box<?> box = boxes.get(i);
            if (box.id() != null && box.rect().contains(px, py)) {
                return box.id();
            }
        }
        return null;
    }

    /**
     * Rectangles are half-open (right/bottom edges belong to the next box), so a point exactly on the
     * canvas' right or bottom edge is moved just inside it to still hit the boxes along that edge.
     */
    private double clampToCanvas(double value, boolean horizontal) {
        if (canvas == null) {
            return value;
        }
        double limit = horizontal ? canvas.resolution().pixelWidth() : canvas.resolution().pixelHeight();
        return value >= limit ? Math.nextDown(limit) : value;
    }
}
