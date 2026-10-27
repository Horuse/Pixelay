package dev.horuse.pixelay.components;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.config.PixelaySettings;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.CanvasResolution;
import dev.horuse.pixelay.utils.FakeEntityIds;
import dev.horuse.pixelay.utils.PacketUtils;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;

/**
 * A flat coloured rectangle on a canvas (backgrounds, debug panels): a fake TextDisplay holding a single space
 * whose background is stretched to the rectangle, sent to one player only.
 */
public final class ColorPanel {

    /**
     * Background of a one-space TextDisplay in font px, as the client draws it: the text width (4)
     * plus 1, and the line height (9) plus 1 (verified in game). The quad is centred half a font px
     * right of the entity and grows up from it.
     */
    private static final double BG_WIDTH_FONT_PX = 5;
    private static final double BG_HEIGHT_FONT_PX = 10;
    private static final double BLOCKS_PER_FONT_PX = 0.025;
    private static final int FULL_BRIGHTNESS = 0xF000F0;

    private ColorPanel() {}

    /** Spawns a panel over canvas px {@code (x, y, width, height)} at {@code depth} blocks off the plane. */
    public static int spawn(
        Player viewer,
        Canvas canvas,
        double x,
        double y,
        double width,
        double height,
        int argb,
        float depth
    ) {
        return spawn(viewer, canvas, x, y, width, height, argb, depth, 0.0, 0.0, 0.0);
    }

    /** Spawns a panel over canvas px {@code (x, y, width, height)} with optional Euler rotation degrees. */
    public static int spawn(
        Player viewer,
        Canvas canvas,
        double x,
        double y,
        double width,
        double height,
        int argb,
        float depth,
        double rotateDeg,
        double rotateXDeg,
        double rotateYDeg
    ) {
        return spawn(
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
            Double.NaN,
            Double.NaN
        );
    }

    /** Spawns a panel over canvas px {@code (x, y, width, height)} with optional Euler rotation degrees around {@code (pivotX, pivotY)}. */
    public static int spawn(
        Player viewer,
        Canvas canvas,
        double x,
        double y,
        double width,
        double height,
        int argb,
        float depth,
        double rotateDeg,
        double rotateXDeg,
        double rotateYDeg,
        double pivotX,
        double pivotY
    ) {
        return spawn(
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
            pivotY,
            FULL_BRIGHTNESS
        );
    }

    /** Spawns a panel over canvas px with brightness override (used for screen transition shader signatures). */
    public static int spawn(
        Player viewer,
        Canvas canvas,
        double x,
        double y,
        double width,
        double height,
        int argb,
        float depth,
        int brightnessOverride
    ) {
        return spawn(
            viewer,
            canvas,
            x,
            y,
            width,
            height,
            argb,
            depth,
            0.0,
            0.0,
            0.0,
            Double.NaN,
            Double.NaN,
            brightnessOverride
        );
    }

    /** Spawns a panel over canvas px {@code (x, y, width, height)} with optional Euler rotation degrees and brightness override. */
    public static int spawn(
        Player viewer,
        Canvas canvas,
        double x,
        double y,
        double width,
        double height,
        int argb,
        float depth,
        double rotateDeg,
        double rotateXDeg,
        double rotateYDeg,
        double pivotX,
        double pivotY,
        int brightnessOverride
    ) {
        int id = FakeEntityIds.next();
        Location origin = origin(canvas, x, y, width, height, depth, rotateDeg, rotateXDeg, rotateYDeg, pivotX, pivotY);
        PacketUtils.spawnEntity(viewer, id, EntityTypes.TEXT_DISPLAY, origin.getX(), origin.getY(), origin.getZ());

        Quaternionf rotation = rotated(canvas.contentRotation(), rotateDeg, rotateXDeg, rotateYDeg);
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0));
        data.add(new EntityData<>(idx.transformationStartDelta(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.transformationDuration(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.positionInterpolationDuration(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.translation(), EntityDataTypes.VECTOR3F, new Vector3f(0f, 0f, 0f)));
        data.add(new EntityData<>(idx.scale(), EntityDataTypes.VECTOR3F, scale(canvas, width, height)));
        data.add(
            new EntityData<>(
                idx.leftRotation(),
                EntityDataTypes.QUATERNION,
                new Quaternion4f(rotation.x, rotation.y, rotation.z, rotation.w)
            )
        );
        data.add(new EntityData<>(idx.rightRotation(), EntityDataTypes.QUATERNION, new Quaternion4f(0f, 0f, 0f, 1f)));
        data.add(new EntityData<>(idx.billboard(), EntityDataTypes.BYTE, (byte) 0));
        data.add(new EntityData<>(idx.brightnessOverride(), EntityDataTypes.INT, brightnessOverride));
        data.add(new EntityData<>(idx.viewRange(), EntityDataTypes.FLOAT, Pixelay.settings().text().viewRange()));
        data.add(new EntityData<>(idx.text(), EntityDataTypes.ADV_COMPONENT, Component.text(" ")));
        data.add(new EntityData<>(idx.backgroundColor(), EntityDataTypes.INT, argb));
        PacketUtils.send(viewer, new WrapperPlayServerEntityMetadata(id, data));
        return id;
    }

    /** Updates the background colour of an existing panel without moving or recreating it. */
    public static void updateColor(Player viewer, int id, int argb) {
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        PacketUtils.send(
            viewer,
            new WrapperPlayServerEntityMetadata(
                id,
                List.of(new EntityData<>(idx.backgroundColor(), EntityDataTypes.INT, argb))
            )
        );
    }

    /** Moves and resizes an existing panel to a new rectangle via translation (no teleport lerp lag). */
    public static void update(
        Player viewer,
        int id,
        Canvas canvas,
        double width,
        double height,
        org.bukkit.util.Vector translation
    ) {
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        PacketUtils.send(
            viewer,
            new WrapperPlayServerEntityMetadata(
                id,
                List.of(
                    new EntityData<>(idx.transformationStartDelta(), EntityDataTypes.INT, 0),
                    new EntityData<>(idx.transformationDuration(), EntityDataTypes.INT, 0),
                    new EntityData<>(idx.scale(), EntityDataTypes.VECTOR3F, scale(canvas, width, height)),
                    new EntityData<>(
                        idx.translation(),
                        EntityDataTypes.VECTOR3F,
                        new Vector3f((float) translation.getX(), (float) translation.getY(), (float) translation.getZ())
                    )
                )
            )
        );
    }

    /** Moves, resizes, and recolors an existing panel in a single metadata packet without recreating it. */
    public static void update(
        Player viewer,
        int id,
        Canvas canvas,
        double width,
        double height,
        org.bukkit.util.Vector translation,
        int argb
    ) {
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        PacketUtils.send(
            viewer,
            new WrapperPlayServerEntityMetadata(
                id,
                List.of(
                    new EntityData<>(idx.transformationStartDelta(), EntityDataTypes.INT, 0),
                    new EntityData<>(idx.transformationDuration(), EntityDataTypes.INT, 0),
                    new EntityData<>(idx.scale(), EntityDataTypes.VECTOR3F, scale(canvas, width, height)),
                    new EntityData<>(
                        idx.translation(),
                        EntityDataTypes.VECTOR3F,
                        new Vector3f((float) translation.getX(), (float) translation.getY(), (float) translation.getZ())
                    ),
                    new EntityData<>(idx.backgroundColor(), EntityDataTypes.INT, argb)
                )
            )
        );
    }

    /** Moves, resizes, rotates, and optionally recolors an existing panel in a single metadata packet. */
    public static void update(
        Player viewer,
        int id,
        Canvas canvas,
        double width,
        double height,
        org.bukkit.util.Vector translation,
        Quaternionf rotation,
        Integer argb
    ) {
        update(viewer, id, canvas, width, height, translation, rotation, argb, 0);
    }

    /** Moves, resizes, rotates, and optionally recolors an existing panel with client interpolation duration in ticks. */
    public static void update(
        Player viewer,
        int id,
        Canvas canvas,
        double width,
        double height,
        org.bukkit.util.Vector translation,
        Quaternionf rotation,
        Integer argb,
        int transformationDuration
    ) {
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(idx.transformationStartDelta(), EntityDataTypes.INT, 0));
        data.add(
            new EntityData<>(idx.transformationDuration(), EntityDataTypes.INT, Math.max(0, transformationDuration))
        );
        data.add(new EntityData<>(idx.scale(), EntityDataTypes.VECTOR3F, scale(canvas, width, height)));
        data.add(
            new EntityData<>(
                idx.leftRotation(),
                EntityDataTypes.QUATERNION,
                new Quaternion4f(rotation.x, rotation.y, rotation.z, rotation.w)
            )
        );
        data.add(
            new EntityData<>(
                idx.translation(),
                EntityDataTypes.VECTOR3F,
                new Vector3f((float) translation.getX(), (float) translation.getY(), (float) translation.getZ())
            )
        );
        if (argb != null) {
            data.add(new EntityData<>(idx.backgroundColor(), EntityDataTypes.INT, argb));
        }
        PacketUtils.send(viewer, new WrapperPlayServerEntityMetadata(id, data));
    }

    public static Quaternionf rotated(Quaternionf base, double rotateDeg, double rotateXDeg, double rotateYDeg) {
        Quaternionf rot = new Quaternionf(base);
        if (rotateDeg != 0.0) {
            rot.rotateZ((float) Math.toRadians(-rotateDeg));
        }
        if (rotateXDeg != 0.0) {
            rot.rotateX((float) Math.toRadians(rotateXDeg));
        }
        if (rotateYDeg != 0.0) {
            rot.rotateY((float) Math.toRadians(rotateYDeg));
        }
        return rot;
    }

    /** Moves and resizes an existing panel to a new rectangle. */
    public static void update(
        Player viewer,
        int id,
        Canvas canvas,
        double x,
        double y,
        double width,
        double height,
        float depth
    ) {
        PacketUtils.send(
            viewer,
            new WrapperPlayServerEntityMetadata(
                id,
                List.of(
                    new EntityData<>(
                        Pixelay.settings().packets().metadata().scale(),
                        EntityDataTypes.VECTOR3F,
                        scale(canvas, width, height)
                    )
                )
            )
        );
        move(viewer, id, canvas, x, y, width, height, depth);
    }

    /** Moves a panel spawned with the same size to a new position. */
    public static void move(
        Player viewer,
        int id,
        Canvas canvas,
        double x,
        double y,
        double width,
        double height,
        float depth
    ) {
        Location origin = origin(canvas, x, y, width, height, depth);
        PacketUtils.send(
            viewer,
            new WrapperPlayServerEntityTeleport(
                id,
                new Vector3d(origin.getX(), origin.getY(), origin.getZ()),
                0f,
                0f,
                false
            )
        );
    }

    /** Non-uniform TextDisplay scale that stretches the one-space background to {@code width x height} canvas px. */
    public static Vector3f scale(Canvas canvas, double width, double height) {
        CanvasResolution res = canvas.resolution();
        float x = (float) (((width / BG_WIDTH_FONT_PX) * (res.blockWidth() / res.pixelWidth())) / BLOCKS_PER_FONT_PX);
        float y = (float) (((height / BG_HEIGHT_FONT_PX) * (res.blockHeight() / res.pixelHeight())) /
            BLOCKS_PER_FONT_PX);
        return new Vector3f(x, y, 1f);
    }

    /** Where a panel's entity stands for the rectangle {@code (x, y, width, height)}. */
    public static Location origin(Canvas canvas, double x, double y, double width, double height, float depth) {
        return origin(canvas, x, y, width, height, depth, 0.0, 0.0, 0.0, Double.NaN, Double.NaN);
    }

    /**
     * Where a panel's entity stands for the rectangle {@code (x, y, width, height)} when rotated by
     * {@code rotateDeg} clockwise around {@code (pivotX, pivotY)} in canvas pixels.
     */
    public static Location origin(
        Canvas canvas,
        double x,
        double y,
        double width,
        double height,
        float depth,
        double rotateDeg,
        double pivotX,
        double pivotY
    ) {
        return origin(canvas, x, y, width, height, depth, rotateDeg, 0.0, 0.0, pivotX, pivotY);
    }

    /**
     * Where a panel's entity stands for the rectangle {@code (x, y, width, height)} when rotated by
     * Euler degrees {@code (rotateDeg, rotateXDeg, rotateYDeg)} around {@code (pivotX, pivotY)} in canvas pixels.
     */
    public static Location origin(
        Canvas canvas,
        double x,
        double y,
        double width,
        double height,
        float depth,
        double rotateDeg,
        double rotateXDeg,
        double rotateYDeg,
        double pivotX,
        double pivotY
    ) {
        double unitX = width / BG_WIDTH_FONT_PX;
        double localDx = -0.5 * unitX;
        double localDy = height / 2.0;
        double centerX = x + width / 2.0;
        double centerY = y + height / 2.0;
        double px = Double.isNaN(pivotX) ? centerX : pivotX;
        double py = Double.isNaN(pivotY) ? centerY : pivotY;

        double unrotatedX = centerX + localDx;
        double unrotatedY = centerY + localDy;

        return canvas.rotatedPixelToWorld(unrotatedX, unrotatedY, depth, rotateDeg, rotateXDeg, rotateYDeg, px, py);
    }
}
