package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.ColorPanel;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.debug.LayoutDebug;
import dev.horuse.pixelay.utils.PacketUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Layout debug wireframe overlay ({@code /pixelay debug layout}): draws crisp 100% opaque wireframe
 * outlines (top/right/bottom/left edges) IN FRONT OF all backgrounds and text, so container hierarchy,
 * spacers, click zones, and overflow errors are clearly visible even over dark/opaque UI surfaces.
 */
final class DebugOverlay {

    private static final double VIEW_DISTANCE = 64;
    private static final double WIRE_THICKNESS = 2.0;

    /** Sits above the box's background and text so opaque backgrounds never hide debug outlines. */
    private static float depthOf(Box<?> box, Player viewer) {
        return Layers.at(box.paintEnd + 0.5, viewer);
    }

    static final int ROW = 0xFF38BDF8;
    static final int COLUMN = 0xFFA3E635;
    static final int GRID = 0xFFC084FC;
    static final int STACK = 0xFF94A3B8;
    static final int SPACER = 0xFFF8FAFC;
    static final int CLICK = 0xFFFACC15;
    static final int OVERFLOW = 0xFFEF4444;

    private record Panel(Rect rect, int color, float depth) {}

    private final Map<UUID, int[]> spawned = new HashMap<>();
    /** What each viewer currently sees, to update panels in place instead of re-spawning them. */
    private final Map<UUID, List<Panel>> shown = new HashMap<>();
    private final Map<UUID, String> canvases = new HashMap<>();

    void update(Box<?> root, Canvas canvas) {
        List<Player> viewers = viewersOf(canvas);
        List<UUID> ids = viewers.stream().map(Player::getUniqueId).toList();
        for (UUID id : new ArrayList<>(spawned.keySet())) {
            if (!ids.contains(id)) {
                destroy(id);
            }
        }
        if (viewers.isEmpty()) {
            return;
        }

        String canvasKey = canvas.center() + "|" + canvas.resolution();

        for (Player viewer : viewers) {
            UUID id = viewer.getUniqueId();
            // Depths are per viewer (debug layers pulls them apart for one player only).
            List<Panel> panels = new ArrayList<>();
            collect(root, panels, viewer);
            List<Panel> previous = shown.get(id);
            if (previous != null && sameStructure(previous, panels) && canvasKey.equals(canvases.get(id))) {
                // Same boxes: only move/resize the ones whose rectangle changed (no flicker, few packets).
                int[] entityIds = spawned.get(id);
                for (int i = 0; i < panels.size(); i++) {
                    Panel panel = panels.get(i);
                    if (panel.equals(previous.get(i))) {
                        continue;
                    }
                    Rect r = panel.rect();
                    if (entityIds[i] == 0) {
                        // Was empty when spawned: create it now that it has a size.
                        if (r.width() > 0 && r.height() > 0) {
                            entityIds[i] = ColorPanel.spawn(
                                viewer,
                                canvas,
                                r.x(),
                                r.y(),
                                r.width(),
                                r.height(),
                                panel.color(),
                                panel.depth()
                            );
                        }
                    } else if (r.width() <= 0 || r.height() <= 0) {
                        // Became empty: remove it (it is spawned again once it has a size).
                        PacketUtils.destroy(viewer, entityIds[i]);
                        entityIds[i] = 0;
                    } else {
                        ColorPanel.update(
                            viewer,
                            entityIds[i],
                            canvas,
                            r.x(),
                            r.y(),
                            r.width(),
                            r.height(),
                            panel.depth()
                        );
                    }
                }
                shown.put(id, panels);
                continue;
            }
            destroy(id);
            spawned.put(id, spawn(viewer, panels, canvas));
            shown.put(id, panels);
            canvases.put(id, canvasKey);
        }
    }

    void clear() {
        for (UUID id : new ArrayList<>(spawned.keySet())) {
            destroy(id);
        }
    }

    private static List<Player> viewersOf(Canvas canvas) {
        Location center = canvas.center();
        List<Player> result = new ArrayList<>();
        for (Player player : LayoutDebug.onlineViewers()) {
            if (player.getWorld().equals(center.getWorld()) && player.getLocation().distance(center) <= VIEW_DISTANCE) {
                result.add(player);
            }
        }
        return result;
    }

    private static void collect(Box<?> box, List<Panel> panels, Player viewer) {
        if (box.isHidden()) {
            return;
        }
        int color = box.fit() == dev.horuse.pixelay.debug.Fit.SELF ? OVERFLOW : colorOf(box);
        float depth = depthOf(box, viewer);
        if (color != 0) {
            addWireframe(panels, box.rect(), color, depth, WIRE_THICKNESS);
        }
        if (box.effectiveOnClick() != null) {
            addWireframe(panels, Insets.all(WIRE_THICKNESS).shrink(box.rect()), CLICK, depth + 0.0005f, WIRE_THICKNESS);
        }
        if (box instanceof Container<?> container) {
            for (Box<?> child : container.children) {
                collect(child, panels, viewer);
            }
        }
    }

    private static void addWireframe(List<Panel> panels, Rect r, int color, float depth, double t) {
        if (r.width() <= 0 || r.height() <= 0) {
            return;
        }
        double hBar = Math.min(t, r.height());
        double vBar = Math.min(t, r.width());
        double midH = Math.max(0, r.height() - 2 * hBar);
        // Top, Bottom, Left, Right wireframe edges
        panels.add(new Panel(new Rect(r.x(), r.y(), r.width(), hBar), color, depth));
        panels.add(new Panel(new Rect(r.x(), Math.max(r.y(), r.bottom() - hBar), r.width(), hBar), color, depth));
        panels.add(new Panel(new Rect(r.x(), r.y() + hBar, vBar, midH), color, depth));
        panels.add(new Panel(new Rect(Math.max(r.x(), r.right() - vBar), r.y() + hBar, vBar, midH), color, depth));
    }

    private static int colorOf(Box<?> box) {
        if (box instanceof Flex flex) {
            return flex.isRow() ? ROW : COLUMN;
        }
        if (box instanceof Grid) {
            return GRID;
        }
        if (box instanceof Stack) {
            return STACK;
        }
        if (box instanceof Spacer) {
            return SPACER;
        }
        return 0;
    }

    /** Same boxes in the same order (rectangles may differ). */
    private static boolean sameStructure(List<Panel> a, List<Panel> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).color() != b.get(i).color()) {
                return false;
            }
        }
        return true;
    }

    /** One entity id per panel, index-aligned with {@code panels} (0 for an empty rectangle). */
    private int[] spawn(Player viewer, List<Panel> panels, Canvas canvas) {
        int[] ids = new int[panels.size()];
        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            Rect rect = panel.rect();
            if (rect.width() > 0 && rect.height() > 0) {
                ids[i] = ColorPanel.spawn(
                    viewer,
                    canvas,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    panel.color(),
                    panel.depth()
                );
            }
        }
        return ids;
    }

    private void destroy(UUID viewerId) {
        int[] ids = spawned.remove(viewerId);
        shown.remove(viewerId);
        canvases.remove(viewerId);
        if (ids != null) {
            PacketUtils.destroy(
                Bukkit.getPlayer(viewerId),
                Arrays.stream(ids)
                    .filter(entity -> entity != 0)
                    .toArray()
            );
        }
    }
}
