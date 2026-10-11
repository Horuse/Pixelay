package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.clicks.CanvasClicks;
import dev.horuse.pixelay.clicks.ClickTarget;
import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.components.CanvasComponent;
import dev.horuse.pixelay.components.TextBackground;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import dev.horuse.pixelay.data.VAlign;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
    /** Panels of boxes with a {@link Box#background}, by box. */
    private final Map<Box<?>, TextBackground> backgrounds = new IdentityHashMap<>();
    private final ClickTarget clickTarget = this::click;
    private Player clickViewer;
    private boolean stepsWarned;
    private boolean anchorsWarned;
    private boolean textOnContainerWarned;
    /** Parent of every box, to give :hover to the ancestors of the hovered box. */
    private final Map<Box<?>, Box<?>> parents = new IdentityHashMap<>();

    /** Where a layout mounted with {@link Layout#mountAt} is pinned; {@code null} = fill the canvas. */
    record Placement(double x, double y, HAlign h, VAlign v) {}

    private final Placement placement;

    MountedLayout(Box<?> root) {
        this(root, null);
    }

    MountedLayout(Box<?> root, Placement placement) {
        this.root = root;
        this.placement = placement;
        root.collectComponents(components);
        root.collectBoxes(boxes);
        mapParents(root);
    }

    private void mapParents(Box<?> box) {
        if (box instanceof Container<?> container) {
            for (Box<?> child : container.children) {
                parents.put(child, box);
                mapParents(child);
            }
        }
    }

    public Box<?> root() {
        return root;
    }

    public List<AbstractCanvasComponent> components() {
        return List.copyOf(components);
    }

    /** Runs measure + arrange over the whole canvas, or at the mount point (see {@link Layout#mountAt}). */
    public void layout(Canvas canvas) {
        this.canvas = canvas;
        // States may change texts (state styles), and so sizes: they go before measuring.
        updateStates();
        // Texts may have changed since the last pass: measure everything once more, then reuse.
        root.invalidate();
        if (placement == null) {
            root.arrange(new Rect(0, 0, canvas.resolution().pixelWidth(), canvas.resolution().pixelHeight()));
        } else {
            arrangeAtPoint(canvas);
        }
        assignSteps();
        int top = 0;
        for (Box<?> box : boxes) {
            top = Math.max(top, box.paintEnd);
        }
        Layers.reportTopStep(canvas, this, top);
    }

    /**
     * Gives every painting box its depth steps in CSS painting order (parents first, earlier siblings
     * first): one step above every earlier box it overlaps. See {@link Layers}.
     */
    private void assignSteps() {
        List<Box<?>> painted = new ArrayList<>();
        for (Box<?> box : boxes) {
            // Every box is a layer (like devtools' 3D view): a container its background and debug panel,
            // a component its text background and text. Nested boxes are always above their parent.
            int needs = box instanceof Leaf ? 2 : 1;
            int base = 1;
            for (Box<?> earlier : painted) {
                if (overlaps(earlier.rect(), box.rect())) {
                    base = Math.max(base, earlier.paintEnd + 1);
                }
            }
            int top = base + needs - 1;
            if (top > Layers.MAX_STEP) {
                base -= top - Layers.MAX_STEP;
                if (!stepsWarned) {
                    stepsWarned = true;
                    Pixelay.plugin()
                        .getLogger()
                        .warning(
                            "A layout overlaps more than " +
                                Layers.MAX_STEP +
                                " painted boxes: the topmost share a depth"
                        );
                }
            }
            box.paintStep = base;
            box.paintEnd = base + needs - 1;
            painted.add(box);
            if (box instanceof Leaf leaf) {
                leaf.component().setLayerStep(base);
            }
        }
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    /** Places a {@link Layout#mountAt} tree: shrink-to-fit within the canvas width, aligned to the point. */
    private void arrangeAtPoint(Canvas canvas) {
        // Like CSS absolute positioning: natural width, but never wider than the canvas (texts wrap).
        Size size = root.measure(canvas.resolution().pixelWidth());
        root.arrange(new Rect(0, 0, size.width(), size.height()));
        // Align the anchored box (or the whole tree) to the point, then place the tree there.
        Rect pinned = anchorBox().rect();
        // The anchor point of a box: its left/centre/right and top/middle/bottom, as for components.
        double dx = placement.x() - (pinned.centerX() - placement.h().horizontalOffset(pinned.width()));
        double dy = placement.y() - (pinned.bottom() - placement.v().bottomOffset(pinned.height()));
        root.arrange(new Rect(dx, dy, size.width(), size.height()));
    }

    /** The box marked {@link Box#anchor()} (the first in painting order if several, with a warning), or the root. */
    private Box<?> anchorBox() {
        Box<?> found = null;
        for (Box<?> box : boxes) {
            if (box.isAnchor()) {
                if (found == null) {
                    found = box;
                } else if (!anchorsWarned) {
                    anchorsWarned = true;
                    Pixelay.plugin()
                        .getLogger()
                        .warning("A mountAt layout has several anchor() boxes: only the first one is used");
                }
            }
        }
        return found != null ? found : root;
    }

    @Override
    public void spawn(Canvas canvas) {
        layout(canvas);
        components.forEach(component -> component.spawn(canvas));
        clickViewer = viewer();
        // Registered after its components, so the layout (boxes with onClick) is asked first.
        CanvasClicks.register(canvas, clickViewer, clickTarget);
        syncBackgrounds();
        debugOverlay.update(root, canvas);
    }

    @Override
    public void despawn() {
        if (canvas != null) {
            CanvasClicks.unregister(canvas, clickViewer, clickTarget);
            Layers.forgetTopStep(canvas, this);
        }
        components.forEach(CanvasComponent::despawn);
        backgrounds.values().forEach(TextBackground::remove);
        backgrounds.clear();
        debugOverlay.clear();
    }

    /** A layout of backgrounds only (no components) has nothing that can die, so it is valid. */
    @Override
    public boolean isValid() {
        return components.stream().allMatch(CanvasComponent::isValid);
    }

    @Override
    public void refresh() {
        if (canvas != null) {
            layout(canvas);
        }
        components.forEach(CanvasComponent::refresh);
        if (canvas != null) {
            syncBackgrounds();
            debugOverlay.update(root, canvas);
        }
    }

    /**
     * Draws box backgrounds. They are seen by the same player as the components (the first one that has
     * a single viewer), or by everyone for server components. Deeper boxes sit closer to the text.
     */
    private void syncBackgrounds() {
        Player viewer = viewer();
        for (Box<?> box : boxes) {
            if (box.effectiveBackground() != 0 || backgrounds.containsKey(box)) {
                backgrounds
                    .computeIfAbsent(box, key -> new TextBackground(viewer))
                    .sync(canvas, box.rect(), box.effectiveBackground(), Layers.at(box.paintStep, viewer));
            }
        }
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

    /**
     * Routes a click at a canvas pixel: the deepest clickable box under the point wins — a box with
     * {@code onClick}, or the whole box of a clickable component (its padding included).
     *
     * @return whether anything handled the click
     */
    public boolean click(double px, double py, Player player) {
        Box<?> target = topmost(px, py, box -> box.effectiveOnClick() != null || box.is(State.DISABLED));
        if (target == null) {
            return false;
        }
        // A disabled box (or one inside it) swallows the click, like CSS :disabled.
        if (!insideDisabled(target)) {
            Consumer<Player> onClick = target.effectiveOnClick();
            if (onClick != null) {
                onClick.accept(player);
            }
        }
        return true;
    }

    /** The topmost box (last in painting order) under the point that matches, or {@code null}. */
    private Box<?> topmost(double px, double py, Predicate<Box<?>> filter) {
        double x = clampToCanvas(px, true);
        double y = clampToCanvas(py, false);
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box<?> box = boxes.get(i);
            if (filter.test(box) && box.rect().contains(x, y)) {
                return box;
            }
        }
        return null;
    }

    /** Whether the box or any box around it is disabled. */
    private boolean insideDisabled(Box<?> box) {
        for (Box<?> at = box; at != null; at = parents.get(at)) {
            if (at.is(State.DISABLED)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves {@code player}'s pointer to a canvas pixel. Like CSS {@code :hover}, the topmost box under it (the
     * one a click would reach) and its ancestors become hovered, not the boxes drawn under it; hover styles
     * apply, enter/leave handlers run and, if anything changed, the layout is refreshed at once. A
     * {@code ScreenSession} calls it for its cursor; call it yourself for any other pointer.
     *
     * @return whether any box changed its hover state
     */
    public boolean pointerAt(double px, double py, Player player) {
        return hover(topmost(px, py, box -> true), player);
    }

    /** The pointer left the layout: nothing is hovered any more. */
    public boolean pointerLeft(Player player) {
        return hover(null, player);
    }

    /** Hovers {@code top} and its ancestors (nothing for {@code null}); refreshes when anything changed. */
    private boolean hover(Box<?> top, Player player) {
        Set<Box<?>> hoveredBoxes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Box<?> box = top; box != null; box = parents.get(box)) {
            hoveredBoxes.add(box);
        }
        boolean changed = false;
        for (Box<?> box : boxes) {
            changed |= setHovered(box, hoveredBoxes.contains(box), player);
        }
        if (changed && canvas != null) {
            refresh();
        }
        return changed;
    }

    private boolean setHovered(Box<?> box, boolean over, Player player) {
        boolean changed = box.setHovered(over, player);
        if (box instanceof Leaf leaf) {
            leaf.component().setHovered(over);
        }
        if (changed) {
            applyStyle(box);
        }
        return changed;
    }

    /** The texts of state styles (backgrounds are read when they are drawn). */
    private void applyStyle(Box<?> box) {
        Style style = box.activeStyle();
        if (box instanceof Leaf leaf) {
            leaf.component().setTextOverride(style != null ? style.text() : null);
        } else if (style != null && style.text() != null && !textOnContainerWarned) {
            textOnContainerWarned = true;
            Pixelay.plugin()
                .getLogger()
                .warning("A state style sets text on a container box: text styles only apply to node(component)");
        }
    }

    /** Re-reads conditional states and applies the state styles, before a layout pass. */
    private void updateStates() {
        for (Box<?> box : boxes) {
            box.updateStates();
            applyStyle(box);
        }
    }

    /** @return the id of the deepest box with an id under the point, or {@code null} */
    public String idAt(double px, double py) {
        Box<?> box = topmost(px, py, candidate -> candidate.id() != null);
        return box != null ? box.id() : null;
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
