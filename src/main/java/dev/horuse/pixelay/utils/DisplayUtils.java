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
 * Stateless helpers for spawning and configuring {@link Display} entities. Kept outside the canvas
 * module because these are useful for any hologram/marker work.
 */
public final class DisplayUtils {

    private DisplayUtils() {}

    /** Tags an entity as Pixelay's (removed by {@code cleanup-on-enable}) and makes it non-persistent. */
    public static void tag(Entity entity) {
        entity.getPersistentDataContainer().set(Pixelay.entityKey(), PersistentDataType.BYTE, (byte) 1);
        entity.setPersistent(false);
    }

    /**
     * @return a Y-axis rotation quaternion for the given yaw in degrees
     */
    public static Quaternionf yawRotation(float yawDegrees) {
        return new Quaternionf().rotateY((float) Math.toRadians(yawDegrees));
    }

    /**
     * Spawns a centered, fixed-billboard {@link TextDisplay} with a transparent background.
     *
     * @param loc world location of the text anchor
     * @param text text component to render
     * @param rotation left rotation applied to the model (so it faces with its surface)
     * @param scale uniform scale
     * @return the spawned, non-persistent TextDisplay
     */
    public static TextDisplay spawnText(Location loc, Component text, Quaternionf rotation, float scale) {
        return spawnText(loc, text, rotation, scale, TextDisplay.TextAlignment.CENTER);
    }

    /**
     * Same as {@link #spawnText(Location, Component, Quaternionf, float)} with an explicit text
     * alignment: the entity position anchors the LEFT/CENTER/RIGHT edge of the block.
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
