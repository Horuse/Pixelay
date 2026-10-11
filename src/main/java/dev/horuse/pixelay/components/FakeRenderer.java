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
 * Draws a {@link CanvasDrawable} as a packet-only TextDisplay of one player: nothing exists on the server,
 * so each player sees their own canvas and others never do. Clicks reach it through
 * {@link dev.horuse.pixelay.clicks.CanvasClicks} (world click tiles or a screen cursor).
 *
 * <p>NOTE: Display/TextDisplay metadata indexes are version-specific; they are read from {@code
 * packets.metadata} in config.yml so they can be recalibrated for another Minecraft version.
 */
public class FakeRenderer extends TextRenderer {

    /** Billboard FIXED — the display is oriented only by its rotation metadata (no auto-face). */
    private static final byte BILLBOARD_FIXED = 0;

    /** TextDisplay style flag bits. */
    private static final byte STYLE_SHADOW = 1;

    private static final byte STYLE_SEE_THROUGH = 2;

    /** Presses the lines of a multi-line block to the left. */
    private static final byte STYLE_LEFT = 8;

    /** Presses the lines of a multi-line block to the right. */
    private static final byte STYLE_RIGHT = 16;

    /** Packed block/sky light 15/15 — forces full brightness. */
    private static final int FULL_BRIGHTNESS = 0xF000F0;

    /** "No override" value of the brightness metadata. */
    private static final int NO_BRIGHTNESS_OVERRIDE = -1;

    private int entityId;
    /** Where the fake entity actually stands; repositioning on refresh is done via translation. */
    private Vector entityPos;
    /** Current display translation (world axes: the entity is spawned with zero yaw/pitch). */
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
        float scale = canvas.scaleForHeightPx(drawable.heightPx());
        PacketUtils.send(viewer, new WrapperPlayServerEntityMetadata(entityId, metadata(placement.component(), scale)));
    }

    @Override
    protected void updateEntity(TextPlacement.Placement placement, Location at) {
        translation = at.toVector().subtract(entityPos);
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        // Text and position go in ONE metadata packet: a separate teleport + metadata pair lets the
        // client render a frame with the new text at the old position (visible as a jerk).
        PacketUtils.send(
            viewer,
            new WrapperPlayServerEntityMetadata(
                entityId,
                List.of(
                    new EntityData<>(idx.text(), EntityDataTypes.ADV_COMPONENT, placement.component()),
                    // The alignment presses multi-line text left/right; it may have changed since the spawn.
                    new EntityData<>(idx.styleFlags(), EntityDataTypes.BYTE, styleFlags(Pixelay.settings().text())),
                    new EntityData<>(
                        idx.translation(),
                        EntityDataTypes.VECTOR3F,
                        new Vector3f((float) translation.getX(), (float) translation.getY(), (float) translation.getZ())
                    )
                )
            )
        );
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
        // Fake entities have no server state; valid until explicitly despawned.
        return entityId != 0;
    }

    /** Teleports the rendered text to a world position (moving elements like a cursor). */
    public void move(Vector worldPos) {
        if (entityId == 0) {
            return;
        }
        // Moved elements (like the cursor) are not refreshed by layouts: keep their background here.
        syncBackground();
        // The translation is kept, so the entity goes where the visible text lands on worldPos.
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

    private List<EntityData<?>> metadata(Component text, float scale) {
        PixelaySettings.Text style = Pixelay.settings().text();
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        Quaternionf rotation = canvas.contentRotation();
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0)); // entity flags
        data.add(new EntityData<>(idx.transformationStartDelta(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.transformationDuration(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.positionInterpolationDuration(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.translation(), EntityDataTypes.VECTOR3F, new Vector3f(0f, 0f, 0f)));
        data.add(new EntityData<>(idx.scale(), EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)));
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
        // Pixelay breaks the lines itself (written into the text): the client must never wrap.
        data.add(new EntityData<>(idx.lineWidth(), EntityDataTypes.INT, TextPlacement.CLIENT_LINE_WIDTH));
        // The background is a separate panel (TextBackground), never the client's own one.
        data.add(new EntityData<>(idx.backgroundColor(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.textOpacity(), EntityDataTypes.BYTE, (byte) -1));
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
     * TextDisplay style bits: shadow/see-through from config plus line alignment of the block (this
     * does not move the block's position).
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
