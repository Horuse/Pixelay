package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.clicks.CanvasClicks;
import dev.horuse.pixelay.clicks.ClickTarget;
import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.components.CanvasComponent;
import dev.horuse.pixelay.components.LayoutComponent;
import dev.horuse.pixelay.components.TextBackground;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import dev.horuse.pixelay.screen.ScreenSession;
import dev.horuse.pixelay.utils.PacketUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Mounts and manages a layout tree on a canvas.
 * Spawning calculates component layout over the canvas surface.
 * Refreshing recalculates bounds and updates positions of moved components.
 */
public final class MountedLayout implements CanvasComponent {

    // Lifecycle methods run on the primary server thread.

    private final Box<?> root;
    private final List<LayoutComponent> components = new ArrayList<>();
    private final List<Box<?>> boxes = new ArrayList<>();
    private dev.horuse.pixelay.data.Canvas canvas;
    private final DebugOverlay debugOverlay = new DebugOverlay();
    private BukkitTask transitionTask;
    /** Background panels mapped by box. */
    private final Map<Box<?>, TextBackground> backgrounds = new IdentityHashMap<>();
    /** Border edge panels mapped by box in top, right, bottom, left order. */
    private final Map<Box<?>, TextBackground[]> borders = new IdentityHashMap<>();
    private final Map<Box<?>, Integer> compositedColors = new IdentityHashMap<>();
    private final ClickTarget clickTarget = this::click;
    private Player explicitViewer;
    private Player clickViewer;
    private boolean stepsWarned;
    private boolean textWithoutTextWarned;
    /** Parent box lookup to propagate hover state to ancestors. */
    private final Map<Box<?>, Box<?>> parents = new IdentityHashMap<>();

    public MountedLayout(Box<?> root) {
        this.root = root;
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

    /** Binds this layout to a single viewing player. */
    public MountedLayout viewer(Player viewer) {
        this.explicitViewer = viewer;
        if (viewer != null) {
            for (Box<?> box : boxes) {
                box.bindViewer(viewer);
            }
        }
        return this;
    }

    public List<LayoutComponent> components() {
        return List.copyOf(components);
    }

    /** Calculates component layout over the canvas bounds. */
    public void layout(dev.horuse.pixelay.data.Canvas canvas) {
        this.canvas = canvas;
        boxes.clear();
        root.collectBoxes(boxes);
        Player v = explicitViewer != null ? explicitViewer : viewer();
        if (v != null) {
            for (Box<?> box : boxes) {
                box.bindViewer(v);
            }
        }
        boxes.sort(Comparator.comparingInt(Box::zIndex));
        parents.clear();
        mapParents(root);
        // State styles can alter text metrics, update states before measurement.
        updateStates();
        // Invalidate previous layout metrics before remeasuring.
        root.invalidate();
        if (root.isAbsolute()) {
            arrangeAbsoluteRoot(canvas);
        } else {
            root.arrange(new Rect(0, 0, canvas.resolution().pixelWidth(), canvas.resolution().pixelHeight()));
        }
        assignClips(root, new Rect(0, 0, canvas.resolution().pixelWidth(), canvas.resolution().pixelHeight()));
        assignSteps();
        int top = 0;
        for (Box<?> box : boxes) {
            top = Math.max(top, box.paintEnd);
        }
        Layers.reportTopStep(canvas, this, top);
    }

    /** Positions an absolute root box within canvas bounds. */
    private void arrangeAbsoluteRoot(dev.horuse.pixelay.data.Canvas canvas) {
        Rect bounds = new Rect(0, 0, canvas.resolution().pixelWidth(), canvas.resolution().pixelHeight());
        Insets m = root.effectiveMargin();
        boolean hasL = !Double.isNaN(root.left);
        boolean hasR = !Double.isNaN(root.right);
        boolean hasT = !Double.isNaN(root.top);
        boolean hasB = !Double.isNaN(root.bottom);

        double availW =
            hasL && hasR
                ? Math.max(0, bounds.width() - root.left - root.right - m.horizontal())
                : Math.max(0, bounds.width() - m.horizontal());
        Size natural = root.measure(availW);

        double w = hasL && hasR && !root.hasFixedWidth() ? availW : natural.width();
        double h =
            hasT && hasB && !root.hasFixedHeight()
                ? Math.max(0, bounds.height() - root.top - root.bottom - m.vertical())
                : natural.height();

        double x;
        if (hasL) {
            x = bounds.x() + root.left + m.left();
        } else if (hasR) {
            x = bounds.right() - root.right - m.right() - w;
        } else {
            Align hAlign = root.justifySelf != null ? root.justifySelf : Align.START;
            x = bounds.x() + m.left() + hAlign.offset(Math.max(0, bounds.width() - m.horizontal()), w);
        }

        double y;
        if (hasT) {
            y = bounds.y() + root.top + m.top();
        } else if (hasB) {
            y = bounds.bottom() - root.bottom - m.bottom() - h;
        } else {
            Align vAlign = root.alignSelf != null ? root.alignSelf : Align.START;
            y = bounds.y() + m.top() + vAlign.offset(Math.max(0, bounds.height() - m.vertical()), h);
        }

        root.arrange(new Rect(x, y, w, h));
    }

    /**
     * Assigns depth steps to boxes in painting order to prevent z-fighting.
     */
    private void assignSteps() {
        List<Box<?>> painted = new ArrayList<>();
        for (Box<?> box : boxes) {
            // Each box receives a depth layer based on painting order.
            int needs = box.layerSteps();
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
            if (box.component() != null) {
                box.component().setLayerStep(box.hasBorder() ? base + 1 : base);
            }
        }
    }

    /** Calculates visible clip bounds for child elements. */
    private static void assignClips(Box<?> box, Rect clip) {
        Rect effectiveClip = box.isHidden() ? Rect.EMPTY : clip;
        box.clip(effectiveClip);
        if (box.component() != null) {
            box.component().setClip(box.isHidden() ? Rect.EMPTY : box.childClip());
        }
        if (box instanceof Container<?> container) {
            Rect inside = box.isHidden() ? Rect.EMPTY : box.childClip();
            for (Box<?> child : container.children) {
                assignClips(child, inside);
            }
        }
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    @Override
    public void spawn(dev.horuse.pixelay.data.Canvas canvas) {
        layout(canvas);
        components.forEach(component -> component.spawn(canvas));
        clickViewer = viewer();
        // Register layout target to handle box click callbacks first.
        CanvasClicks.register(canvas, clickViewer, clickTarget);
        syncBackgrounds();
        debugOverlay.update(root, canvas);
    }

    @Override
    public void despawn() {
        cancelTransitionTask();
        if (canvas != null) {
            CanvasClicks.unregister(canvas, clickViewer, clickTarget);
            Layers.forgetTopStep(canvas, this);
            this.canvas = null;
        }
        components.forEach(CanvasComponent::despawn);
        for (Box<?> box : boxes) {
            if (box instanceof Canvas pixelCanvas) {
                pixelCanvas.clearQuads();
            }
        }
        backgrounds.values().forEach(TextBackground::remove);
        backgrounds.clear();
        for (TextBackground[] edges : borders.values()) {
            for (TextBackground edge : edges) {
                if (edge != null) {
                    edge.remove();
                }
            }
        }
        borders.clear();
        compositedColors.clear();
        debugOverlay.clear();
    }

    /** Validates whether mounted components remain active. */
    @Override
    public boolean isValid() {
        return components.stream().allMatch(CanvasComponent::isValid);
    }

    @Override
    public void refresh() {
        if (canvas != null) {
            layout(canvas);
        }
        Player viewer = viewer();
        if (viewer != null) {
            PacketUtils.sendBundle(viewer);
        }
        components.forEach(CanvasComponent::refresh);
        if (canvas != null) {
            syncBackgrounds();
            debugOverlay.update(root, canvas);
        }
        if (viewer != null) {
            PacketUtils.sendBundle(viewer);
        }
    }

    /**
     * Renders and updates background panels for boxes.
     * Alpha composites translucent child boxes with ancestor backgrounds to prevent depth buffer artifacts.
     */
    private void syncBackgrounds() {
        Player viewer = viewer();
        compositedColors.clear();
        for (Box<?> box : boxes) {
            int bg = compositedBackground(box);
            if (bg != 0) {
                compositedColors.put(box, bg);
            }
            if (bg != 0 || backgrounds.containsKey(box)) {
                backgrounds
                    .computeIfAbsent(box, key -> new TextBackground(viewer))
                    .sync(
                        canvas,
                        box.rect().intersect(box.clip()),
                        bg,
                        Layers.at(box.paintStep, viewer),
                        box.visualRotateDeg(),
                        box.visualRotateXDeg(),
                        box.visualRotateYDeg(),
                        box.visualPivotX(),
                        box.visualPivotY(),
                        box.visualTransformationDurationTicks()
                    );
            }
            syncBoxBorders(box, viewer, bg);
            if (box instanceof Canvas pixelCanvas) {
                pixelCanvas.sync(canvas, viewer, bg, Layers.at(box.paintStep + 1, viewer));
            }
        }
    }

    private void syncBoxBorders(Box<?> box, Player viewer, int boxBg) {
        Insets rawB = box.effectiveBorderWidths();
        Insets b = new Insets(
            rawB.top() * box.visualScaleY(),
            rawB.right() * box.visualScaleX(),
            rawB.bottom() * box.visualScaleY(),
            rawB.left() * box.visualScaleX()
        );
        int rawBorderColor = box.effectiveBorderColor();
        boolean active =
            !box.isHidden() && rawBorderColor != 0 && (b.top() > 0 || b.right() > 0 || b.bottom() > 0 || b.left() > 0);
        if (!active && !borders.containsKey(box)) {
            return;
        }
        TextBackground[] edges = borders.computeIfAbsent(box, k -> new TextBackground[] {
            new TextBackground(viewer),
            new TextBackground(viewer),
            new TextBackground(viewer),
            new TextBackground(viewer),
        });
        int borderArgb = active ? (boxBg != 0 ? composite(boxBg, rawBorderColor) : rawBorderColor) : 0;
        Rect r = box.rect();
        Rect clip = box.clip();
        float depth = Layers.at(box.paintStep + 1, viewer);

        double rotZ = box.visualRotateDeg();
        double rotX = box.visualRotateXDeg();
        double rotY = box.visualRotateYDeg();
        double pivX = box.visualPivotX();
        double pivY = box.visualPivotY();
        int durationTicks = box.visualTransformationDurationTicks();
        boolean hasRot = Math.abs(rotZ) >= 1e-4 || Math.abs(rotX) >= 1e-4 || Math.abs(rotY) >= 1e-4;

        Rect topRect = b.top() > 0 ? new Rect(r.x(), r.y(), r.width(), Math.min(b.top(), r.height())) : Rect.EMPTY;
        Rect bottomRect =
            b.bottom() > 0
                ? new Rect(r.x(), Math.max(r.y(), r.bottom() - b.bottom()), r.width(), Math.min(b.bottom(), r.height()))
                : Rect.EMPTY;
        double midY = r.y() + b.top();
        double midH = Math.max(0, r.height() - b.top() - b.bottom());
        Rect leftRect = b.left() > 0 ? new Rect(r.x(), midY, Math.min(b.left(), r.width()), midH) : Rect.EMPTY;
        Rect rightRect =
            b.right() > 0
                ? new Rect(Math.max(r.x(), r.right() - b.right()), midY, Math.min(b.right(), r.width()), midH)
                : Rect.EMPTY;

        if (!hasRot && clip != null) {
            topRect = topRect.intersect(clip);
            bottomRect = bottomRect.intersect(clip);
            leftRect = leftRect.intersect(clip);
            rightRect = rightRect.intersect(clip);
        }

        edges[0].sync(canvas, topRect, borderArgb, depth, rotZ, rotX, rotY, pivX, pivY, durationTicks);
        edges[1].sync(canvas, rightRect, borderArgb, depth, rotZ, rotX, rotY, pivX, pivY, durationTicks);
        edges[2].sync(canvas, bottomRect, borderArgb, depth, rotZ, rotX, rotY, pivX, pivY, durationTicks);
        edges[3].sync(canvas, leftRect, borderArgb, depth, rotZ, rotX, rotY, pivX, pivY, durationTicks);
    }

    private int compositedBackground(Box<?> box) {
        int own = box.effectiveBackground();
        if (own == 0 || own >>> 24 == 255) {
            return own;
        }
        int bgUnder = 0;
        // Search ancestor containers first.
        for (Box<?> p = parents.get(box); p != null; p = parents.get(p)) {
            int pBg = compositedColors.getOrDefault(p, 0);
            if (pBg == 0) {
                pBg = p.effectiveBackground();
            }
            if (pBg != 0 && p.rect() != null && box.rect() != null && !p.rect().intersect(box.rect()).isEmpty()) {
                bgUnder = pBg;
                break;
            }
        }
        // Fall back to overlapping earlier boxes.
        if (bgUnder == 0 && box.rect() != null) {
            int topStep = -1;
            for (Box<?> earlier : boxes) {
                if (earlier == box) {
                    break;
                }
                int eBg = compositedColors.getOrDefault(earlier, 0);
                if (eBg == 0) {
                    eBg = earlier.effectiveBackground();
                }
                if (eBg != 0 && earlier.rect() != null && overlaps(earlier.rect(), box.rect())) {
                    if (earlier.paintStep > topStep) {
                        topStep = earlier.paintStep;
                        bgUnder = eBg;
                    }
                }
            }
        }
        if (bgUnder == 0) {
            return own;
        }
        return composite(bgUnder, own);
    }

    /**
     * Blends two colors using Porter-Duff Source Over alpha composition.
     */
    public static int composite(int bgUnder, int bgOver) {
        int aOver = (bgOver >>> 24) & 0xFF;
        if (aOver == 0) {
            return bgUnder;
        }
        if (aOver == 255) {
            return bgOver;
        }
        int aUnder = (bgUnder >>> 24) & 0xFF;
        if (aUnder == 0) {
            return bgOver;
        }

        float alphaOver = aOver / 255.0f;
        float alphaUnder = (aUnder / 255.0f) * (1.0f - alphaOver);
        float alphaOut = alphaOver + alphaUnder;

        if (alphaOut <= 0.001f) {
            return 0;
        }

        int rOver = (bgOver >>> 16) & 0xFF;
        int gOver = (bgOver >>> 8) & 0xFF;
        int bOver = bgOver & 0xFF;

        int rUnder = (bgUnder >>> 16) & 0xFF;
        int gUnder = (bgUnder >>> 8) & 0xFF;
        int bUnder = bgUnder & 0xFF;

        int rOut = Math.min(255, Math.max(0, Math.round((rOver * alphaOver + rUnder * alphaUnder) / alphaOut)));
        int gOut = Math.min(255, Math.max(0, Math.round((gOver * alphaOver + gUnder * alphaUnder) / alphaOut)));
        int bOut = Math.min(255, Math.max(0, Math.round((bOver * alphaOver + bUnder * alphaUnder) / alphaOut)));
        int aOutInt = Math.min(255, Math.max(0, Math.round(alphaOut * 255.0f)));

        return (aOutInt << 24) | (rOut << 16) | (gOut << 8) | bOut;
    }

    /** Returns the single viewing player for this layout, or null if shared. */
    private Player viewer() {
        if (explicitViewer != null) {
            return explicitViewer;
        }
        Player compViewer = components
            .stream()
            .map(LayoutComponent::viewer)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        if (compViewer != null) {
            return compViewer;
        }
        return boxes.stream().map(Box::viewer).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /**
     * Routes a click at canvas coordinates to the deepest matching box.
     *
     * @return true if an element handled the click
     */
    public boolean click(double px, double py, Player player) {
        if (ScreenSession.isInSession(player)) {
            pointerAt(px, py, player);
        } else {
            for (Box<?> box : boxes) {
                if (box instanceof ScrollView scrollView) {
                    scrollView.updatePointer(px, py);
                }
            }
            if (player != null && dev.horuse.pixelay.debug.LayoutDebug.isEnabled(player)) {
                Box<?> inspectBox = topmost(px, py, box -> !box.isHidden());
                if (inspectBox != null) {
                    Rect r = inspectBox.rect();
                    dev.horuse.pixelay.debug.LayoutDebug.sendInspector(
                        player,
                        inspectBox.tagName(),
                        inspectBox.id(),
                        r.x(),
                        r.y(),
                        r.width(),
                        r.height(),
                        inspectBox.paintStep,
                        inspectBox.zIndex()
                    );
                }
            }
        }
        Box<?> target = topmost(
            px,
            py,
            box -> !box.isHidden() && (box.effectiveOnClick() != null || box.is(State.DISABLED))
        );
        if (target == null) {
            return false;
        }
        // Disabled boxes consume click events without action.
        if (!insideDisabled(target)) {
            Box.ClickHandler onClick = target.effectiveOnClick();
            if (onClick != null) {
                onClick.onClick(player, px, py);
            }
        }
        if (canvas != null) {
            refresh();
        }
        return true;
    }

    /** Finds the topmost box under the given point within active clip bounds. */
    private Box<?> topmost(double px, double py, Predicate<Box<?>> filter) {
        double x = clampToCanvas(px, true);
        double y = clampToCanvas(py, false);
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box<?> box = boxes.get(i);
            if (
                !box.isHidden() &&
                filter.test(box) &&
                box.contains(x, y) &&
                (box.clip() == null || box.clip().contains(x, y))
            ) {
                return box;
            }
        }
        return null;
    }

    /** Determines the cursor sprite name for the given canvas coordinate. */
    public String cursorAt(double px, double py) {
        Box<?> top = topmost(px, py, box -> true);
        boolean disabled = insideDisabled(top);
        for (Box<?> box = top; box != null; box = parents.get(box)) {
            String cursor = box.effectiveCursor();
            if (cursor != null && !cursor.equals("auto")) {
                return cursor;
            }
            if (box.effectiveOnClick() != null && !disabled) {
                return "pointer";
            }
        }
        return "default";
    }

    /** Checks if the box or any ancestor has disabled state active. */
    private boolean insideDisabled(Box<?> box) {
        for (Box<?> at = box; at != null; at = parents.get(at)) {
            if (at.is(State.DISABLED)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Updates hovered box state for the player at canvas coordinates.
     *
     * @return true if hover state changed
     */
    public boolean pointerAt(double px, double py, Player player) {
        for (Box<?> box : boxes) {
            if (box instanceof ScrollView scrollView) {
                scrollView.updatePointer(px, py);
            }
        }
        return hover(topmost(px, py, box -> true), player);
    }

    /** Finds the innermost scrollable container under canvas coordinates. */
    public ScrollView scrollableAt(double px, double py) {
        Box<?> top = topmost(px, py, box -> true);
        for (Box<?> at = top; at != null; at = parents.get(at)) {
            if (at instanceof ScrollView scrollView && scrollView.canScroll()) {
                return scrollView;
            }
        }
        for (Box<?> box : boxes) {
            if (box instanceof ScrollView scrollView && scrollView.canScroll()) {
                return scrollView;
            }
        }
        return null;
    }

    /**
     * Advances smooth scrolling animations in active scroll views.
     *
     * @return true if any scroll position changed
     */
    public boolean tickScroll() {
        boolean changed = false;
        for (Box<?> box : boxes) {
            if (box instanceof ScrollView scrollView) {
                if (scrollView.tick()) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Advances property transitions for all boxes in the layout.
     *
     * @return true if transitions are currently active
     */
    public boolean tickTransitions() {
        boolean active = false;
        long now = System.currentTimeMillis();
        for (Box<?> box : boxes) {
            if (box.tickTransitions(now)) {
                active = true;
            }
        }
        return active;
    }

    void ensureTransitionTicking() {
        Player v = viewer();
        if (transitionTask != null || canvas == null || (v != null && ScreenSession.isInSession(v))) {
            return;
        }
        transitionTask = Bukkit.getScheduler().runTaskTimer(
            Pixelay.plugin(),
            () -> {
                if (canvas == null || !isValid()) {
                    cancelTransitionTask();
                    return;
                }
                if (tickTransitions()) {
                    refresh();
                } else {
                    cancelTransitionTask();
                }
            },
            1,
            1
        );
    }

    private void cancelTransitionTask() {
        if (transitionTask != null) {
            transitionTask.cancel();
            transitionTask = null;
        }
    }

    /** Returns any active scroll view with a dragged scrollbar thumb. */
    public ScrollView activeDraggingScrollView() {
        for (Box<?> box : boxes) {
            if (box instanceof ScrollView scrollView && scrollView.isDragging()) {
                return scrollView;
            }
        }
        return null;
    }

    /** Returns any canvas currently undergoing brush drag operations. */
    public Canvas activeDrawingCanvas() {
        for (Box<?> box : boxes) {
            if (box instanceof Canvas pixelCanvas && pixelCanvas.isDrawing()) {
                return pixelCanvas;
            }
        }
        return null;
    }

    /** Clears hover state from all elements in the layout. */
    public boolean pointerLeft(Player player) {
        return hover(null, player);
    }

    /** Applies hover state to the target box and its ancestors. */
    private boolean hover(Box<?> top, Player player) {
        if (top != null && player != null && dev.horuse.pixelay.debug.LayoutDebug.isEnabled(player)) {
            Rect r = top.rect();
            dev.horuse.pixelay.debug.LayoutDebug.sendInspector(
                player,
                top.tagName(),
                top.id(),
                r.x(),
                r.y(),
                r.width(),
                r.height(),
                top.paintStep,
                top.zIndex()
            );
        }
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
        if (box.component() != null) {
            box.component().setHovered(over);
        }
        if (changed) {
            applyStyle(box);
            if (box.hasTransitions()) {
                ensureTransitionTicking();
            }
        }
        return changed;
    }

    /** Applies text overrides from active styles. */
    private void applyStyle(Box<?> box) {
        Style style = box.activeStyle();
        if (box.component() instanceof AbstractCanvasComponent textComponent) {
            textComponent.setTextOverride(style != null ? style.text() : null);
        } else if (style != null && style.text() != null && !textWithoutTextWarned) {
            textWithoutTextWarned = true;
            Pixelay.plugin()
                .getLogger()
                .warning("A state style sets text on a box without text: text styles only apply to text/button boxes");
        }
    }

    /** Evaluates conditional states and applies matching styles. */
    private void updateStates() {
        for (Box<?> box : boxes) {
            box.updateStates();
            applyStyle(box);
        }
    }

    /** @return identifier of the deepest identified box under coordinates */
    public String idAt(double px, double py) {
        Box<?> box = topmost(px, py, candidate -> candidate.id() != null);
        return box != null ? box.id() : null;
    }

    /** Clamps coordinates slightly inside the canvas edge for half-open bounds. */
    private double clampToCanvas(double value, boolean horizontal) {
        if (canvas == null) {
            return value;
        }
        double limit = horizontal ? canvas.resolution().pixelWidth() : canvas.resolution().pixelHeight();
        return value >= limit ? Math.nextDown(limit) : value;
    }
}
