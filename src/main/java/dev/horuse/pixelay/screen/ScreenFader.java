package dev.horuse.pixelay.screen;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.components.ColorPanel;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.utils.PacketUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * A packet-only transition veil over a canvas driven by phase parameter {@code u}.
 * Phase {@code 0} is out of view, {@code 1} is fully covered, and {@code 2} is completed.
 * Covering animates {@code 0 -> 1} and reveal animates {@code 1 -> 2}.
 * <p>
 * Updates use client-side GPU interpolation ({@link #INTERPOLATION_TICKS}) to match monitor refresh rate.
 */
final class ScreenFader {

    /** Duration in ticks for client-side GPU interpolation. */
    private static final int INTERPOLATION_TICKS = 2;
    private static final double SQRT2 = Math.sqrt(2);

    private final Player player;
    private final Canvas canvas;
    private final ScreenTransition transition;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final float depth;
    private final int color;
    private int id1;
    private int id2;
    private Location origin1;
    private Location origin2;
    private BukkitTask task;
    private boolean removed;

    ScreenFader(
        Player player,
        Canvas canvas,
        ScreenTransition transition,
        double x,
        double y,
        double width,
        double height,
        float depth,
        double u
    ) {
        this.player = player;
        this.canvas = canvas;
        this.transition = transition;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.color = transition.overlayColor() & 0x00FFFFFF;
        spawn(u);
    }

    /** Animates phase from {@code from} to {@code to}, then executes {@code done} when interpolation completes. */
    void animate(double from, double to, Runnable done) {
        cancel();
        long duration = Math.max(1, transition.durationMs());
        long start = System.currentTimeMillis();
        task = Bukkit.getScheduler().runTaskTimer(
            Pixelay.plugin(),
            () -> {
                if (removed || !player.isOnline()) {
                    remove();
                    return;
                }
                double raw = Math.min(1.0, (System.currentTimeMillis() - start) / (double) duration);
                double eased = Math.max(0.0, Math.min(1.0, transition.easing().ease(raw)));
                apply(from + (to - from) * eased);
                if (raw >= 1.0) {
                    cancel();
                    // Wait for client to finish interpolation before executing callback.
                    task = Bukkit.getScheduler().runTaskLater(
                        Pixelay.plugin(),
                        () -> {
                            task = null;
                            if (!removed && done != null) {
                                done.run();
                            }
                        },
                        INTERPOLATION_TICKS
                    );
                }
            },
            1,
            1
        );
    }

    /** Waits {@code ticks} while covered, then performs reveal animation and removes itself. */
    void revealAfter(long ticks, Runnable onRevealed) {
        cancel();
        task = Bukkit.getScheduler().runTaskLater(
            Pixelay.plugin(),
            () ->
                animate(1, 2, () -> {
                    remove();
                    if (onRevealed != null) {
                        onRevealed.run();
                    }
                }),
            Math.max(1, ticks)
        );
    }

    void remove() {
        cancel();
        if (removed) {
            return;
        }
        removed = true;
        if (player.isOnline()) {
            if (id1 != 0) {
                PacketUtils.destroy(player, id1);
                id1 = 0;
            }
            if (id2 != 0) {
                PacketUtils.destroy(player, id2);
                id2 = 0;
            }
        }
    }

    private void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // --- layout & movement -------------------------------------------------------------------------------

    private boolean isCorner() {
        return switch (transition.type()) {
            case CORNER_WIPE_TOP_LEFT, CORNER_WIPE_TOP_RIGHT, CORNER_WIPE_BOTTOM_LEFT, CORNER_WIPE_BOTTOM_RIGHT -> true;
            default -> false;
        };
    }

    private void spawn(double u) {
        switch (transition.type()) {
            case FADE -> {
                id1 = ColorPanel.spawn(player, canvas, x, y, width, height, argb(alpha(u)), depth);
                origin1 = ColorPanel.origin(canvas, x, y, width, height, depth);
            }
            case CURTAIN -> {
                double open = Math.abs(u - 1);
                double halfW = halfWidth();
                double lx = leftX(open);
                double rx = rightX(open);
                id1 = ColorPanel.spawn(player, canvas, lx, y, halfW, height, argb(255), depth);
                origin1 = ColorPanel.origin(canvas, lx, y, halfW, height, depth);
                id2 = ColorPanel.spawn(player, canvas, rx, y, halfW, height, argb(255), depth);
                origin2 = ColorPanel.origin(canvas, rx, y, halfW, height, depth);
            }
            default -> {
                if (isCorner()) {
                    double size = diagonalSize();
                    double px = cornerX(u);
                    double py = cornerY(u);
                    double pivotX = px + size / 2.0;
                    double pivotY = py + size / 2.0;
                    // Spawn panel rotated 45 degrees around its center.
                    id1 = ColorPanel.spawn(
                        player,
                        canvas,
                        px,
                        py,
                        size,
                        size,
                        argb(255),
                        depth,
                        45.0,
                        0.0,
                        0.0,
                        pivotX,
                        pivotY
                    );
                    origin1 = ColorPanel.origin(canvas, px, py, size, size, depth, 45.0, pivotX, pivotY);
                } else {
                    double px = wipeX(u);
                    double py = wipeY(u);
                    id1 = ColorPanel.spawn(player, canvas, px, py, width, height, argb(255), depth);
                    origin1 = ColorPanel.origin(canvas, px, py, width, height, depth);
                }
            }
        }
    }

    private void apply(double u) {
        if (removed) {
            return;
        }
        switch (transition.type()) {
            case FADE -> ColorPanel.updateColor(player, id1, argb(alpha(u)));
            case CURTAIN -> {
                double open = Math.abs(u - 1);
                double halfW = halfWidth();
                move(id1, origin1, leftX(open), y, halfW, height, 0.0);
                move(id2, origin2, rightX(open), y, halfW, height, 0.0);
            }
            default -> {
                if (isCorner()) {
                    double size = diagonalSize();
                    move(id1, origin1, cornerX(u), cornerY(u), size, size, 45.0);
                } else {
                    move(id1, origin1, wipeX(u), wipeY(u), width, height, 0.0);
                }
            }
        }
    }

    private void move(int id, Location origin, double px, double py, double w, double h, double rotateDeg) {
        Location at =
            rotateDeg != 0.0
                ? ColorPanel.origin(canvas, px, py, w, h, depth, rotateDeg, px + w / 2.0, py + h / 2.0)
                : ColorPanel.origin(canvas, px, py, w, h, depth);
        Vector shift = at.toVector().subtract(origin.toVector());
        ColorPanel.update(player, id, canvas, w, h, shift, null, null, INTERPOLATION_TICKS);
    }

    private double halfWidth() {
        return width / 2.0 + 4.0;
    }

    private double leftX(double open) {
        return x - halfWidth() * open;
    }

    private double rightX(double open) {
        return x + width / 2.0 - 4.0 + halfWidth() * open;
    }

    private double wipeX(double u) {
        return x + dirX() * (u - 1) * width;
    }

    private double wipeY(double u) {
        return y + dirY() * (u - 1) * height;
    }

    /** Side length of 45-degree square that encloses the overlay with 3 percent margin. */
    private double diagonalSize() {
        return Math.round(((width + height) / SQRT2) * 1.03);
    }

    /** Distance from overlay center to entry corner, plus half square size. */
    private double diagonalTravel() {
        double dCorner = (width + height) / (2.0 * SQRT2);
        return dCorner + diagonalSize() / 2.0;
    }

    private double cornerX(double u) {
        double centerX = x + width / 2.0 + (u - 1.0) * diagonalTravel() * (dirX() / SQRT2);
        return centerX - diagonalSize() / 2.0;
    }

    private double cornerY(double u) {
        double centerY = y + height / 2.0 + (u - 1.0) * diagonalTravel() * (dirY() / SQRT2);
        return centerY - diagonalSize() / 2.0;
    }

    /** Horizontal travel direction of a wipe (1 for right, -1 for left). */
    private int dirX() {
        return switch (transition.type()) {
            case LINEAR_WIPE_LEFT, CORNER_WIPE_TOP_RIGHT, CORNER_WIPE_BOTTOM_RIGHT -> -1;
            case LINEAR_WIPE_RIGHT, CORNER_WIPE_TOP_LEFT, CORNER_WIPE_BOTTOM_LEFT -> 1;
            default -> 0;
        };
    }

    /** Vertical travel direction of a wipe (1 for down, -1 for up). */
    private int dirY() {
        return switch (transition.type()) {
            case LINEAR_WIPE_UP, CORNER_WIPE_BOTTOM_LEFT, CORNER_WIPE_BOTTOM_RIGHT -> -1;
            case LINEAR_WIPE_DOWN, CORNER_WIPE_TOP_LEFT, CORNER_WIPE_TOP_RIGHT -> 1;
            default -> 0;
        };
    }

    private static int alpha(double u) {
        return (int) Math.round(255 * Math.max(0.0, Math.min(1.0, 1.0 - Math.abs(u - 1.0))));
    }

    private int argb(int alpha) {
        return (alpha << 24) | color;
    }
}
