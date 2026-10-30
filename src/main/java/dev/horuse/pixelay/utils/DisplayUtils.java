package dev.horuse.pixelay.utils;

import dev.horuse.pixelay.Pixelay;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Utility helpers for creating and configuring display entities.
 */
public final class DisplayUtils {

    private DisplayUtils() {}

    /** Marks an entity as non-persistent and owned by Pixelay. */
    public static void tag(Entity entity) {
        entity.getPersistentDataContainer().set(Pixelay.entityKey(), PersistentDataType.BYTE, (byte) 1);
        entity.setPersistent(false);
    }

    /**
     * @return A quaternion for Y-axis rotation by given yaw degrees.
     */
    public static Quaternionf yawRotation(float yawDegrees) {
        return new Quaternionf().rotateY((float) Math.toRadians(yawDegrees));
    }

    /**
     * Spawns a centered {@link TextDisplay} entity with a transparent background.
     *
     * @param loc World location of text anchor.
     * @param text Text component to render.
     * @param rotation Left rotation applied to display model.
     * @param scale Uniform scale factor.
     * @return The created non-persistent TextDisplay entity.
     */
    public static TextDisplay spawnText(Location loc, Component text, Quaternionf rotation, float scale) {
        return spawnText(loc, text, rotation, scale, TextDisplay.TextAlignment.CENTER);
    }

    /**
     * Spawns a {@link TextDisplay} entity with explicit horizontal text alignment.
     */
    public static TextDisplay spawnText(
        Location loc,
        Component text,
        Quaternionf rotation,
        float scale,
        TextDisplay.TextAlignment alignment
    ) {
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class);
        display.text(text);
        display.setAlignment(alignment);
        display.setSeeThrough(false);
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowed(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setTransformation(
            new Transformation(
                new Vector3f(),
                new Quaternionf(rotation),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
            )
        );
        display.setPersistent(false);
        return display;
    }
}
