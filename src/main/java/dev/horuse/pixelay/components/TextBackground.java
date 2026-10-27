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
 * The background of a text element ({@code text.background}, or the layout debug highlight), drawn as
 * a separate panel exactly over the element's box. The client's own TextDisplay background is not
 * used: it is uneven (1 font px wider on the left and on top, and it covers the trailing spacing).
 * <p>
 * With a viewer the panel is a fake entity sent to that player only; without one it is a server
 * entity visible to everyone. A fully transparent colour removes the panel.
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

    /** @param viewer the only player who sees the panel, or {@code null} for a server entity everyone sees */
    public TextBackground(Player viewer) {
        this.viewer = viewer;
    }

    /** The colour an element's background should have now. */
    static int colorOf(CanvasDrawable drawable, boolean debugging) {
        if (!drawable.debugBackground()) {
            return 0;
        }
        return LayoutDebug.background(drawable.fit(), debugging, Pixelay.settings().text().backgroundArgb());
    }

    /** Shows the panel over the element's box ({@code hitbox} as measured), in place when it already exists. */
    void sync(Canvas canvas, CanvasDrawable drawable, Size hitbox, int argb) {
        Rect bounds = hitbox != null ? TextPlacement.bounds(drawable, hitbox) : null;
        // Cut like the text it is under (CSS overflow).
        if (bounds != null && drawable.clip() != null) {
            bounds = bounds.intersect(drawable.clip());
        }
        sync(canvas, bounds, argb, drawable.textBackgroundDepth());
    }

    /**
     * Shows the panel over {@code bounds} (canvas px) at {@code depth} blocks
     * off the plane, in place when it already exists; {@code null}, empty bounds or a transparent colour
     * remove it.
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
        boolean empty = bounds == null || bounds.width() <= 0 || bounds.height() <= 0 || argb >>> 24 == 0;
        if (empty) {
            remove();
            return;
        }
        boolean sameRot =
            Math.abs(rotateDeg - lastRotateDeg) < 1e-3 &&
            Math.abs(rotateXDeg - lastRotateXDeg) < 1e-3 &&
            Math.abs(rotateYDeg - lastRotateYDeg) < 1e-3 &&
            (Math.abs(rotateDeg) < 1e-3 ||
                (Math.abs(pivotX - lastPivotX) < 1e-2 && Math.abs(pivotY - lastPivotY) < 1e-2));
        if (exists() && bounds.equals(box) && depth == lastDepth && sameRot) {
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
        Location origin = ColorPanel.origin(canvas, x, y, width, height, depth, rotateDeg, pivotX, pivotY);
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
                    argb != color ? argb : null
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
    }

    private boolean exists() {
        return fakeId != 0 || entity != null;
    }

    private static Vector3f toJoml(com.github.retrooper.packetevents.util.Vector3f v) {
        return new Vector3f(v.getX(), v.getY(), v.getZ());
    }
}
