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
import dev.horuse.pixelay.utils.FakeEntityIds;
import dev.horuse.pixelay.utils.PacketUtils;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/**
 * Renders a {@link CanvasDrawable} as a packet-only TextDisplay for one player without server entities.
 * Each player views their private canvas. {@link dev.horuse.pixelay.clicks.CanvasClicks} routes clicks
 * through world click tiles or a screen cursor.
 *
 * <p>NOTE: Display and TextDisplay metadata indexes vary by Minecraft version.
 * Configuration setting {@code packets.metadata} provides the indexes.
 */
public class FakeRenderer extends TextRenderer {

    /** Billboard FIXED flag. Display orientation depends only on rotation metadata. */
    private static final byte BILLBOARD_FIXED = 0;

    /** TextDisplay style flag bits. */
    private static final byte STYLE_SHADOW = 1;

    private static final byte STYLE_SEE_THROUGH = 2;

    /** Aligns lines of a multiline text block to the left. */
    private static final byte STYLE_LEFT = 8;

    /** Aligns lines of a multiline text block to the right. */
    private static final byte STYLE_RIGHT = 16;

    /** Packed block and sky light 15/15 for full brightness. */
    private static final int FULL_BRIGHTNESS = 0xF000F0;

    /** Default brightness metadata value without override. */
    private static final int NO_BRIGHTNESS_OVERRIDE = -1;

    private int entityId;
    /** Spawn location of the fake entity. Refreshes update position with translation metadata. */
    private Vector entityPos;
    /** Current display translation in world axes. */
    private Vector translation = new Vector();

    public FakeRenderer(Player viewer, CanvasDrawable drawable) {
        super(drawable, viewer);
    }

    @Override
    protected void spawnEntity(TextPlacement.Placement placement, Location at) {
        entityId = FakeEntityIds.next();
        entityPos = at.toVector();
        translation = new Vector();
        PacketUtils.spawnEntity(viewer, entityId, EntityTypes.TEXT_DISPLAY, at.getX(), at.getY(), at.getZ());
        float baseScale = canvas.scaleForHeightPx(drawable.heightPx());
        Vector3f scaleVec = new Vector3f(
            (float) (baseScale * drawable.visualScaleX()),
            (float) (baseScale * drawable.visualScaleY()),
            baseScale
        );
        PacketUtils.send(
            viewer,
            new WrapperPlayServerEntityMetadata(
                entityId,
                metadata(placement.component(), scaleVec, effectiveRotation())
            )
        );
    }

    @Override
    protected void updateEntity(TextPlacement.Placement placement, Location at) {
        translation = at.toVector().subtract(entityPos);
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        float baseScale = canvas.scaleForHeightPx(drawable.heightPx());
        Vector3f scaleVec = new Vector3f(
            (float) (baseScale * drawable.visualScaleX()),
            (float) (baseScale * drawable.visualScaleY()),
            baseScale
        );
        Quaternionf rot = effectiveRotation();
        // Send text and position in one metadata packet to prevent rendering new text at old positions.
        PacketUtils.send(
            viewer,
            new WrapperPlayServerEntityMetadata(
                entityId,
                List.of(
                    new EntityData<>(idx.transformationStartDelta(), EntityDataTypes.INT, 0),
                    new EntityData<>(
                        idx.transformationDuration(),
                        EntityDataTypes.INT,
                        drawable.transformationDuration()
                    ),
                    new EntityData<>(idx.text(), EntityDataTypes.ADV_COMPONENT, placement.component()),
                    // Send cut codes and opacity marker together (see TextClip).
                    new EntityData<>(idx.textOpacity(), EntityDataTypes.BYTE, textOpacity()),
                    // Apply multiline alignment in case it changed after spawn.
                    new EntityData<>(idx.styleFlags(), EntityDataTypes.BYTE, styleFlags(Pixelay.settings().text())),
                    new EntityData<>(idx.scale(), EntityDataTypes.VECTOR3F, scaleVec),
                    new EntityData<>(
                        idx.leftRotation(),
                        EntityDataTypes.QUATERNION,
                        new Quaternion4f(rot.x, rot.y, rot.z, rot.w)
                    ),
                    new EntityData<>(
                        idx.translation(),
                        EntityDataTypes.VECTOR3F,
                        new Vector3f((float) translation.getX(), (float) translation.getY(), (float) translation.getZ())
                    )
                )
            )
        );
    }

    private Quaternionf effectiveRotation() {
        Quaternionf rot = new Quaternionf(canvas.contentRotation());
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
        if (entityId != 0) {
            PacketUtils.destroy(viewer, entityId);
        }
        entityId = 0;
    }

    @Override
    protected boolean entityValid() {
        // Fake entities have no server state and stay valid until despawn.
        return entityId != 0;
    }

    /** Teleports the rendered text to target world coordinates for moving elements like cursors. */
    public void move(Vector worldPos) {
        if (entityId == 0) {
            return;
        }
        // Layouts do not refresh moving elements, so update their background here.
        syncBackground();
        // Preserve translation so visible text aligns with worldPos.
        entityPos = worldPos.clone().subtract(translation);
        PacketUtils.send(
            viewer,
            new WrapperPlayServerEntityTeleport(
                entityId,
                new Vector3d(entityPos.getX(), entityPos.getY(), entityPos.getZ()),
                0f,
                0f,
                false
            )
        );
    }

    private List<EntityData<?>> metadata(Component text, Vector3f scale, Quaternionf rotation) {
        PixelaySettings.Text style = Pixelay.settings().text();
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0)); // entity flags
        data.add(new EntityData<>(idx.transformationStartDelta(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.transformationDuration(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.positionInterpolationDuration(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.translation(), EntityDataTypes.VECTOR3F, new Vector3f(0f, 0f, 0f)));
        data.add(new EntityData<>(idx.scale(), EntityDataTypes.VECTOR3F, scale));
        data.add(
            new EntityData<>(
                idx.leftRotation(),
                EntityDataTypes.QUATERNION,
                new Quaternion4f(rotation.x, rotation.y, rotation.z, rotation.w)
            )
        );
        data.add(new EntityData<>(idx.rightRotation(), EntityDataTypes.QUATERNION, new Quaternion4f(0f, 0f, 0f, 1f)));
        data.add(new EntityData<>(idx.billboard(), EntityDataTypes.BYTE, BILLBOARD_FIXED));
        data.add(new EntityData<>(idx.viewRange(), EntityDataTypes.FLOAT, style.viewRange()));
        data.add(new EntityData<>(idx.text(), EntityDataTypes.ADV_COMPONENT, text));
        // Pixelay inserts line breaks into text, so the client must not wrap lines.
        data.add(new EntityData<>(idx.lineWidth(), EntityDataTypes.INT, TextPlacement.CLIENT_LINE_WIDTH));
        // Render background via TextBackground instead of entity defaults.
        data.add(new EntityData<>(idx.backgroundColor(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.textOpacity(), EntityDataTypes.BYTE, textOpacity()));
        data.add(
            new EntityData<>(
                idx.brightnessOverride(),
                EntityDataTypes.INT,
                style.fullBrightness() ? FULL_BRIGHTNESS : NO_BRIGHTNESS_OVERRIDE
            )
        );
        data.add(new EntityData<>(idx.styleFlags(), EntityDataTypes.BYTE, styleFlags(style)));
        return data;
    }

    /**
     * Calculates TextDisplay style bits from configuration and text alignment.
     */
    private byte styleFlags(PixelaySettings.Text style) {
        byte flags = switch (drawable.hAlign()) {
            case LEFT -> STYLE_LEFT;
            case RIGHT -> STYLE_RIGHT;
            case CENTER -> 0;
        };
        if (style.shadow()) {
            flags |= STYLE_SHADOW;
        }
        if (style.seeThrough()) {
            flags |= STYLE_SEE_THROUGH;
        }
        return flags;
    }
}
