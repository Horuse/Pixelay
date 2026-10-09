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

    private final Box root;
    private final List<AbstractCanvasComponent> components = new ArrayList<>();
    private final List<Box> boxes = new ArrayList<>();
    private Canvas canvas;

    MountedLayout(Box root) {
        this.root = root;
        root.collectComponents(components);
        root.collectBoxes(boxes);
    }

    public Box root() {
        return root;
    }

    public List<AbstractCanvasComponent> components() {
        return List.copyOf(components);
    }

    /** Runs measure + arrange over the whole canvas. */
    public void layout(Canvas canvas) {
        this.canvas = canvas;
        root.arrange(new Rect(0, 0, canvas.resolution().pixelWidth(), canvas.resolution().pixelHeight()));
    }

    @Override
    public void spawn(Canvas canvas) {
        layout(canvas);
        components.forEach(component -> component.spawn(canvas));
    }

    @Override
    public void despawn() {
        components.forEach(CanvasComponent::despawn);
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
    }

    /**
     * Routes a click at a canvas pixel: the deepest box with {@code onClick} under the point wins,
     * otherwise a clickable component whose measured hitbox contains the point.
     *
     * @return whether anything handled the click
     */
    public boolean click(double px, double py, Player player) {
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box box = boxes.get(i);
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
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box box = boxes.get(i);
            if (box.id() != null && box.rect().contains(px, py)) {
                return box.id();
            }
        }
        return null;
    }
}
