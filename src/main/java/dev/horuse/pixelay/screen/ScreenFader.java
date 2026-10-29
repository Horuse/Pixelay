package dev.horuse.pixelay.screen;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.components.ColorPanel;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.utils.PacketUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * A packet-only veil over a canvas, driven by one phase value {@code u}: {@code 0} = not yet in view,
 * {@code 1} = fully covered, {@code 2} = gone out the other side. Covering animates {@code 0 → 1}, revealing
 * {@code 1 → 2}, so a wipe keeps moving in one direction through both phases.
 */
final class ScreenFader {

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

    /** Animates the phase from {@code from} to {@code to}, then runs {@code done}. */
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
                    if (done != null) {
                        done.run();
                    }
                }
            },
            1,
            1
        );
    }

    /** Waits {@code ticks} fully covered, then reveals and removes itself. */
    void revealAfter(long ticks) {
        cancel();
        task = Bukkit.getScheduler().runTaskLater(Pixelay.plugin(), () -> animate(1, 2, this::remove), ticks);
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
            }
            if (id2 != 0) {
                PacketUtils.destroy(player, id2);
            }
        }
    }

    private void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void spawn(double u) {
        switch (transition.type()) {
            case FADE, DISSOLVE -> {
                id1 = ColorPanel.spawn(player, canvas, x, y, width, height, argb(alpha(u)), depth);
                origin1 = ColorPanel.origin(canvas, x, y, width, height, depth);
            }
            case CURTAIN -> {
                double open = Math.abs(u - 1);
                double half = halfWidth();
                double left = leftX(open);
                double right = rightX(open);
                id1 = ColorPanel.spawn(player, canvas, left, y, half, height, argb(255), depth);
                origin1 = ColorPanel.origin(canvas, left, y, half, height, depth);
                id2 = ColorPanel.spawn(player, canvas, right, y, half, height, argb(255), depth);
                origin2 = ColorPanel.origin(canvas, right, y, half, height, depth);
            }
            default -> {
                double px = wipeX(u);
                double py = wipeY(u);
                id1 = ColorPanel.spawn(player, canvas, px, py, width, height, argb(255), depth);
                origin1 = ColorPanel.origin(canvas, px, py, width, height, depth);
            }
        }
    }

    private void apply(double u) {
        if (removed) {
            return;
        }
        switch (transition.type()) {
            case FADE, DISSOLVE -> ColorPanel.updateColor(player, id1, argb(alpha(u)));
            case CURTAIN -> {
                double open = Math.abs(u - 1);
                double half = halfWidth();
                move(id1, origin1, leftX(open), y, half);
                move(id2, origin2, rightX(open), y, half);
            }
            default -> move(id1, origin1, wipeX(u), wipeY(u), width);
        }
    }

    private void move(int id, Location origin, double px, double py, double w) {
        Location at = ColorPanel.origin(canvas, px, py, w, height, depth);
        ColorPanel.update(player, id, canvas, w, height, at.toVector().subtract(origin.toVector()));
    }

    private double halfWidth() {
        return width / 2.0 + 4.0;
    }

    private double leftX(double open) {
        return x - (width / 2.0) * open;
    }

    private double rightX(double open) {
        return x + width / 2.0 - 4.0 + (width / 2.0) * open;
    }

    private double wipeX(double u) {
        return x + dirX() * (u - 1) * width;
    }

    private double wipeY(double u) {
        return y + dirY() * (u - 1) * height;
    }

    /** Horizontal travel of a wipe, in canvas px (+ = right). */
    private int dirX() {
        return switch (transition.type()) {
            case LINEAR_WIPE_LEFT, CORNER_WIPE_TOP_RIGHT, CORNER_WIPE_BOTTOM_RIGHT -> -1;
            case LINEAR_WIPE_RIGHT, CORNER_WIPE_TOP_LEFT, CORNER_WIPE_BOTTOM_LEFT -> 1;
            default -> 0;
        };
    }

    /** Vertical travel of a wipe, in canvas px (+ = down). */
    private int dirY() {
        return switch (transition.type()) {
            case LINEAR_WIPE_UP, CORNER_WIPE_BOTTOM_LEFT, CORNER_WIPE_BOTTOM_RIGHT -> -1;
            case LINEAR_WIPE_DOWN, CORNER_WIPE_TOP_LEFT, CORNER_WIPE_TOP_RIGHT -> 1;
            default -> 0;
        };
    }

    private static int alpha(double u) {
        return (int) Math.round(255 * Math.max(0.0, Math.min(1.0, 1 - Math.abs(u - 1))));
    }

    private int argb(int alpha) {
        return (alpha << 24) | color;
    }
}
