package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.clicks.CanvasClicks;
import dev.horuse.pixelay.clicks.ClickTarget;
import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.components.CanvasComponent;
import dev.horuse.pixelay.components.CanvasLayout;
import dev.horuse.pixelay.components.TextBackground;
import dev.horuse.pixelay.data.Canvas;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * A layout tree attached to a canvas. It is itself a {@link CanvasComponent}: spawning lays the tree
 * out over the whole canvas and spawns every component; refreshing re-runs the layout (texts may have
 * changed size) and moves components that shifted.
 */
public final class MountedLayout implements CanvasComponent {

    private static final int MAX_BACKGROUND_LEVEL = 12;

    // spawn/refresh/despawn must run on the main server thread: the debug overlay uses the Bukkit API.

    private final Box<?> root;
    private final List<AbstractCanvasComponent> components = new ArrayList<>();
    private final List<Box<?>> boxes = new ArrayList<>();
    private Canvas canvas;
    private final DebugOverlay debugOverlay = new DebugOverlay();
    /** Panels of boxes with a {@link Box#background}, by box. */
    private final Map<Box<?>, TextBackground> backgrounds = new IdentityHashMap<>();
    private final ClickTarget clickTarget = this::click;
    private Player clickViewer;

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
        clickViewer = viewer();
        // Registered after its components, so the layout (boxes with onClick) is asked first.
        CanvasClicks.register(canvas, clickViewer, clickTarget);
        syncBackgrounds();
        debugOverlay.update(root, components, canvas);
    }

    @Override
    public void despawn() {
        if (canvas != null) {
            CanvasClicks.unregister(canvas, clickViewer, clickTarget);
        }
        components.forEach(CanvasComponent::despawn);
        backgrounds.values().forEach(TextBackground::remove);
        backgrounds.clear();
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
            syncBackgrounds();
            debugOverlay.update(root, components, canvas);
        }
    }

    /**
     * Draws box backgrounds. They are seen by the same player as the components (the first one that has
     * a single viewer), or by everyone for server components. Deeper boxes sit closer to the text.
     */
    private void syncBackgrounds() {
        syncBackgrounds(root, 0, viewer());
    }

    /** The only player who sees this layout (the first component with a single viewer), or {@code null}. */
    private Player viewer() {
        return components
            .stream()
            .map(AbstractCanvasComponent::viewer)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private void syncBackgrounds(Box<?> box, int level, Player viewer) {
        if (box.background() != 0 || backgrounds.containsKey(box)) {
            Rect r = box.rect();
            backgrounds
                .computeIfAbsent(box, key -> new TextBackground(viewer))
                .sync(
                    canvas,
                    new double[] { r.x(), r.y(), r.right(), r.bottom() },
                    box.background(),
                    backgroundDepth(level)
                );
        }
        if (box instanceof Container<?> container) {
            for (Box<?> child : container.children) {
                syncBackgrounds(child, level + 1, viewer);
            }
        }
    }

    /** Box backgrounds stay below the layout debug panels and the text backgrounds. */
    private static float backgroundDepth(int level) {
        float text = Pixelay.settings().text().normalOffset();
        return text * (0.05f + (0.13f * Math.min(level, MAX_BACKGROUND_LEVEL)) / MAX_BACKGROUND_LEVEL);
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
