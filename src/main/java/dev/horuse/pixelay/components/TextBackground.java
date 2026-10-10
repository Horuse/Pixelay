package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.debug.DebugPanel;
import dev.horuse.pixelay.debug.LayoutDebug;
import dev.horuse.pixelay.utils.DisplayUtils;
import java.util.Arrays;
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
final class TextBackground {

    /** Between the canvas (and the layout debug panels) and the text. */
    private static float depth() {
        return Pixelay.settings().text().normalOffset() * 0.95f;
    }

    private final Player viewer;
    private int fakeId;
    private TextDisplay entity;
    private int color;
    private double[] box;

    TextBackground(Player viewer) {
        this.viewer = viewer;
    }

    /** The colour an element's background should have now. */
    static int colorOf(CanvasDrawable drawable, boolean debugging) {
        if (!drawable.debugBackground()) {
            return 0;
        }
        return LayoutDebug.background(drawable.overflow(), debugging, Pixelay.settings().text().backgroundArgb());
    }

    /** Shows the panel over the element's box ({@code hitbox} as measured), in place when it already exists. */
    void sync(Canvas canvas, CanvasDrawable drawable, double[] hitbox, int argb) {
        double[] next = hitbox != null ? CanvasLayout.clickBounds(drawable, hitbox) : null;
        boolean empty = next == null || next[2] <= next[0] || next[3] <= next[1] || argb >>> 24 == 0;
        if (empty) {
            remove();
            return;
        }
        if (exists() && argb == color && Arrays.equals(next, box)) {
            return;
        }
        if (exists() && argb != color) {
            remove();
        }
        double x = next[0];
        double y = next[1];
        double width = next[2] - next[0];
        double height = next[3] - next[1];
        if (viewer != null) {
            if (fakeId == 0) {
                fakeId = DebugPanel.spawn(viewer, canvas, x, y, width, height, argb, depth());
            } else {
                DebugPanel.update(viewer, fakeId, canvas, x, y, width, height, depth());
            }
        } else {
            Location origin = DebugPanel.origin(canvas, x, y, width, height, depth());
            Vector3f scale = toJoml(DebugPanel.scale(canvas, width, height));
            if (entity == null) {
                entity = DisplayUtils.spawnText(origin, Component.text(" "), canvas.contentRotation(), 1f);
                entity.setBackgroundColor(Color.fromARGB(argb));
                entity.setViewRange(Pixelay.settings().text().viewRange());
                entity.setBrightness(new Display.Brightness(15, 15));
                AbstractCanvasComponent.tag(entity);
            } else {
                entity.teleport(origin);
            }
            entity.setTransformation(
                new Transformation(new Vector3f(), new Quaternionf(canvas.contentRotation()), scale, new Quaternionf())
            );
        }
        color = argb;
        box = next;
    }

    void remove() {
        if (fakeId != 0) {
            DebugPanel.destroy(viewer, fakeId);
            fakeId = 0;
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
