package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import dev.horuse.pixelay.debug.LayoutDebug;
import dev.horuse.pixelay.utils.DisplayUtils;
import dev.horuse.pixelay.utils.PacketUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders the background panel of a text element over its calculated bounds.
 * Does not use vanilla TextDisplay backgrounds to ensure accurate dimensions.
 * <p>
 * For private viewers, renders as a packet entity.
 * For public views, renders as a server entity.
 * A fully transparent color removes the panel.
 */
public final class TextBackground {

    private final Player viewer;
    private int fakeId;
    private org.bukkit.util.Vector fakeOrigin;
    private TextDisplay entity;
    private int color;
    private Rect box;
    private float lastDepth;
    private double lastRotateDeg;
    private double lastRotateXDeg;
    private double lastRotateYDeg;
    private double lastPivotX = Double.NaN;
    private double lastPivotY = Double.NaN;
    private Canvas lastCanvas;

    /** @param viewer player who sees the panel, or {@code null} for a shared server entity */
    public TextBackground(Player viewer) {
        this.viewer = viewer;
    }

    /** Calculates current background color for the specified drawable. */
    static int colorOf(CanvasDrawable drawable, boolean debugging) {
        if (!drawable.debugBackground()) {
            return 0;
        }
        return LayoutDebug.background(drawable.fit(), debugging, Pixelay.settings().text().backgroundArgb());
    }

    /** Synchronizes the panel over the measured element bounds. */
    void sync(Canvas canvas, CanvasDrawable drawable, Size hitbox, int argb) {
        sync(canvas, drawable, hitbox, argb, 0);
    }

    /** Synchronizes the panel over the measured element bounds with transformation duration in ticks. */
    void sync(Canvas canvas, CanvasDrawable drawable, Size hitbox, int argb, int transformationDurationTicks) {
        Rect bounds = hitbox != null ? TextPlacement.bounds(drawable, hitbox) : null;
        // Clip background to match text overflow boundaries.
        if (bounds != null && drawable.clip() != null) {
            bounds = bounds.intersect(drawable.clip());
        }
        sync(
            canvas,
            bounds,
            argb,
            drawable.textBackgroundDepth(),
            0.0,
            0.0,
            0.0,
            Double.NaN,
            Double.NaN,
            transformationDurationTicks
        );
    }

    /**
     * Synchronizes the panel over {@code bounds} in canvas pixels at {@code depth} blocks.
     * Null bounds, empty bounds, or transparent colors remove the panel.
     */
    public void sync(Canvas canvas, Rect bounds, int argb, float depth) {
        sync(canvas, bounds, argb, depth, 0.0, 0.0, 0.0, Double.NaN, Double.NaN);
    }

    public void sync(
        Canvas canvas,
        Rect bounds,
        int argb,
        float depth,
        double rotateDeg,
        double rotateXDeg,
        double rotateYDeg
    ) {
        sync(canvas, bounds, argb, depth, rotateDeg, rotateXDeg, rotateYDeg, Double.NaN, Double.NaN);
    }

    public void sync(
        Canvas canvas,
        Rect bounds,
        int argb,
        float depth,
        double rotateDeg,
        double rotateXDeg,
        double rotateYDeg,
        double pivotX,
        double pivotY
    ) {
        sync(canvas, bounds, argb, depth, rotateDeg, rotateXDeg, rotateYDeg, pivotX, pivotY, 0);
    }

    public void sync(
        Canvas canvas,
        Rect bounds,
        int argb,
        float depth,
        double rotateDeg,
        double rotateXDeg,
        double rotateYDeg,
        double pivotX,
        double pivotY,
        int transformationDurationTicks
    ) {
        boolean empty = bounds == null || bounds.width() <= 0 || bounds.height() <= 0 || argb >>> 24 == 0;
        if (empty) {
            remove();
            return;
        }
        boolean hasRotation =
            Math.abs(rotateDeg) >= 1e-3 || Math.abs(rotateXDeg) >= 1e-3 || Math.abs(rotateYDeg) >= 1e-3;
        boolean sameRot =
            Math.abs(rotateDeg - lastRotateDeg) < 1e-3 &&
            Math.abs(rotateXDeg - lastRotateXDeg) < 1e-3 &&
            Math.abs(rotateYDeg - lastRotateYDeg) < 1e-3 &&
            (!hasRotation || (Math.abs(pivotX - lastPivotX) < 1e-2 && Math.abs(pivotY - lastPivotY) < 1e-2));
        boolean sameCanvas =
            canvas == lastCanvas ||
            (lastCanvas != null &&
                canvas.center().equals(lastCanvas.center()) &&
                Math.abs(canvas.yawDegrees() - lastCanvas.yawDegrees()) < 1e-3 &&
                Math.abs(canvas.pitchDegrees() - lastCanvas.pitchDegrees()) < 1e-3);
        if (exists() && bounds.equals(box) && depth == lastDepth && sameRot && sameCanvas) {
            if (argb != color) {
                if (viewer != null) {
                    ColorPanel.updateColor(viewer, fakeId, argb);
                } else if (entity != null) {
                    entity.setBackgroundColor(Color.fromARGB(argb));
                }
                color = argb;
            }
            return;
        }
        double x = bounds.x();
        double y = bounds.y();
        double width = bounds.width();
        double height = bounds.height();
        Location origin = ColorPanel.origin(
            canvas,
            x,
            y,
            width,
            height,
            depth,
            rotateDeg,
            rotateXDeg,
            rotateYDeg,
            pivotX,
            pivotY
        );
        Quaternionf rotation = ColorPanel.rotated(canvas.contentRotation(), rotateDeg, rotateXDeg, rotateYDeg);
        if (viewer != null) {
            if (fakeId == 0) {
                fakeOrigin = origin.toVector();
                fakeId = ColorPanel.spawn(
                    viewer,
                    canvas,
                    x,
                    y,
                    width,
                    height,
                    argb,
                    depth,
                    rotateDeg,
                    rotateXDeg,
                    rotateYDeg,
                    pivotX,
                    pivotY
                );
            } else {
                org.bukkit.util.Vector translation = origin.toVector().subtract(fakeOrigin);
                ColorPanel.update(
                    viewer,
                    fakeId,
                    canvas,
                    width,
                    height,
                    translation,
                    rotation,
                    argb != color ? argb : null,
                    transformationDurationTicks
                );
            }
        } else {
            Vector3f scale = toJoml(ColorPanel.scale(canvas, width, height));
            if (entity == null) {
                entity = DisplayUtils.spawnText(origin, Component.text(" "), rotation, 1f);
                entity.setBackgroundColor(Color.fromARGB(argb));
                entity.setViewRange(Pixelay.settings().text().viewRange());
                entity.setBrightness(new Display.Brightness(15, 15));
                DisplayUtils.tag(entity);
            } else {
                entity.teleport(origin);
                if (argb != color) {
                    entity.setBackgroundColor(Color.fromARGB(argb));
                }
            }
            entity.setInterpolationDuration(transformationDurationTicks);
            entity.setInterpolationDelay(0);
            entity.setTransformation(
                new Transformation(new Vector3f(), new Quaternionf(rotation), scale, new Quaternionf())
            );
        }
        color = argb;
        box = bounds;
        lastDepth = depth;
        lastRotateDeg = rotateDeg;
        lastRotateXDeg = rotateXDeg;
        lastRotateYDeg = rotateYDeg;
        lastPivotX = pivotX;
        lastPivotY = pivotY;
        lastCanvas = canvas;
    }

    /**
     * Teleports the background panel to a new canvas with client-side GPU interpolation.
     */
    public void teleport(Canvas targetCanvas, int durationTicks) {
        if (!exists() || box == null) {
            return;
        }
        double x = box.x();
        double y = box.y();
        double width = box.width();
        double height = box.height();
        Location origin = ColorPanel.origin(
            targetCanvas,
            x,
            y,
            width,
            height,
            lastDepth,
            lastRotateDeg,
            lastRotateXDeg,
            lastRotateYDeg,
            lastPivotX,
            lastPivotY
        );
        Quaternionf rotation = ColorPanel.rotated(
            targetCanvas.contentRotation(),
            lastRotateDeg,
            lastRotateXDeg,
            lastRotateYDeg
        );

        if (viewer != null) {
            if (fakeOrigin == null) {
                fakeOrigin = origin.toVector();
            }
            org.bukkit.util.Vector translation = origin.toVector().subtract(fakeOrigin);
            ColorPanel.update(viewer, fakeId, targetCanvas, width, height, translation, rotation, null, durationTicks);
        } else if (entity != null) {
            entity.setTeleportDuration(durationTicks);
            entity.setInterpolationDuration(durationTicks);
            entity.setInterpolationDelay(0);
            entity.teleport(origin);
        }
        lastCanvas = targetCanvas;
    }

    public void remove() {
        if (fakeId != 0) {
            PacketUtils.destroy(viewer, fakeId);
            fakeId = 0;
            fakeOrigin = null;
        }
        if (entity != null) {
            entity.remove();
            entity = null;
        }
        box = null;
        color = 0;
        lastCanvas = null;
    }

    private boolean exists() {
        return fakeId != 0 || entity != null;
    }

    private static Vector3f toJoml(com.github.retrooper.packetevents.util.Vector3f v) {
        return new Vector3f(v.getX(), v.getY(), v.getZ());
    }
}
