package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.config.PixelaySettings;
import dev.horuse.pixelay.utils.DisplayUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

/**
 * Draws a {@link CanvasDrawable} with a real server {@link TextDisplay}: seen by everyone, or — with a viewer —
 * hidden from everyone else through Paper's per-player visibility (the server sends it to that player only).
 */
public class EntityRenderer extends TextRenderer {

    private TextDisplay display;

    /** @param viewer the only player who sees the element, or {@code null} for everyone */
    public EntityRenderer(CanvasDrawable drawable, Player viewer) {
        super(drawable, viewer);
    }

    @Override
    protected void spawnEntity(TextPlacement.Placement placement, Location at) {
        float baseScale = canvas.scaleForHeightPx(drawable.heightPx());
        display = DisplayUtils.spawnText(
            at,
            placement.component(),
            effectiveRotation(),
            baseScale,
            drawable.hAlign().textAlignment()
        );
        applyScaleAndRotation(display, baseScale);
        if (viewer != null) {
            display.setVisibleByDefault(false);
            viewer.showEntity(Pixelay.plugin(), display);
        }
        applyTextStyle(display);
        display.setTextOpacity(textOpacity());
        DisplayUtils.tag(display);
    }

    @Override
    protected void updateEntity(TextPlacement.Placement placement, Location at) {
        // Position first, then text: the block must not blink with the new text at the old position.
        display.teleport(at);
        applyScaleAndRotation(display, canvas.scaleForHeightPx(drawable.heightPx()));
        // The alignment presses multi-line text left/right; it may have changed since the spawn.
        display.setAlignment(drawable.hAlign().textAlignment());
        display.text(placement.component());
        display.setTextOpacity(textOpacity());
    }

    private void applyScaleAndRotation(TextDisplay display, float baseScale) {
        org.joml.Vector3f scaleVec = new org.joml.Vector3f(
            (float) (baseScale * drawable.visualScaleX()),
            (float) (baseScale * drawable.visualScaleY()),
            baseScale
        );
        display.setInterpolationDuration(drawable.transformationDuration());
        display.setInterpolationDelay(0);
        display.setTransformation(
            new org.bukkit.util.Transformation(
                new org.joml.Vector3f(),
                effectiveRotation(),
                scaleVec,
                new org.joml.Quaternionf()
            )
        );
    }

    private org.joml.Quaternionf effectiveRotation() {
        org.joml.Quaternionf rot = new org.joml.Quaternionf(canvas.contentRotation());
        if (drawable.rotateDeg() != 0.0) {
            rot.rotateZ((float) Math.toRadians(-drawable.rotateDeg()));
        }
        if (drawable.rotateXDeg() != 0.0) {
            rot.rotateX((float) Math.toRadians(drawable.rotateXDeg()));
        }
        if (drawable.rotateYDeg() != 0.0) {
            rot.rotateY((float) Math.toRadians(drawable.rotateYDeg()));
        }
        return rot;
    }

    @Override
    protected void removeEntity() {
        if (display != null) {
            display.remove();
            display = null;
        }
    }

    @Override
    protected boolean entityValid() {
        return display != null && display.isValid();
    }

    /** The shared text style from config.yml ({@code text.*}). */
    private static void applyTextStyle(TextDisplay display) {
        PixelaySettings.Text style = Pixelay.settings().text();
        display.setShadowed(style.shadow());
        display.setSeeThrough(style.seeThrough());
        // The configured background is drawn as a separate panel (TextBackground).
        display.setBackgroundColor(Color.fromARGB(0));
        display.setLineWidth(TextPlacement.CLIENT_LINE_WIDTH);
        display.setViewRange(style.viewRange());
        if (style.fullBrightness()) {
            display.setBrightness(new Display.Brightness(15, 15));
        }
    }
}
