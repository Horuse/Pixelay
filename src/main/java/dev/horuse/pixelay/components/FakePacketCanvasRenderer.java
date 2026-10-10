package dev.horuse.pixelay.components;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.clicks.CanvasClicks;
import dev.horuse.pixelay.clicks.ClickTarget;
import dev.horuse.pixelay.config.PixelaySettings;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.debug.LayoutDebug;
import dev.horuse.pixelay.utils.PacketUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Draws a {@link CanvasDrawable} as fully fake entities: nothing is spawned on the server — only
 * {@code SpawnEntity} + {@code EntityMetadata} packets are sent to a single viewer, so each player
 * sees their own canvas and others never do. Clickable elements do NOT get any hitbox entities:
 * clicks are resolved by the owning surface via hit-testing (see {@link CanvasClickCatcher} for how
 * the raw click arrives in spectator mode).
 *
 * <p>NOTE: Display/TextDisplay metadata indexes are version-specific; they are read from {@code
 * packets.metadata} in config.yml so they can be recalibrated for another Minecraft version.
 */
public class FakePacketCanvasRenderer implements CanvasRenderer {

    private static final AtomicInteger ENTITY_ID = new AtomicInteger();
    private static volatile boolean idBaseApplied;

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

    private final Player viewer;
    private final CanvasDrawable drawable;

    private Canvas canvas;
    private int textEntityId;
    private String lastRendered;
    private int lastLayoutVersion;
    private int lastLineWidth;
    private final TextBackground background;
    /** Where the fake entity actually stands; repositioning on refresh is done via translation. */
    private Vector entityPos;
    /** Current display translation (world axes: the entity is spawned with zero yaw/pitch). */
    private Vector translation = new Vector();
    private double[] measuredHitbox;
    /** Click target of a clickable element: its exact box on the canvas. */
    private final ClickTarget clickTarget = this::clickAt;

    public FakePacketCanvasRenderer(Player viewer, CanvasDrawable drawable) {
        this.viewer = viewer;
        this.drawable = drawable;
        this.background = new TextBackground(viewer);
    }

    @Override
    public void spawn(Canvas canvas) {
        if (isValid()) {
            return;
        }

        this.canvas = canvas;

        String text = drawable.renderText();
        measuredHitbox = CanvasLayout.hitboxSizePx(drawable, text);
        double[] pos = CanvasLayout.displayPosition(drawable, text);
        Vector worldPos = canvas.pixelToWorld(pos[0], pos[1], drawable.textDepth()).toVector();
        lastRendered = text;
        lastLayoutVersion = drawable.layoutVersion();
        lastLineWidth = drawable.lineWidth();
        entityPos = worldPos;
        translation = new Vector();
        textEntityId = spawnText(worldPos, text);
        syncBackground();
        if (drawable.onClick() != null) {
            CanvasClicks.register(canvas, viewer, clickTarget);
        }
        Pixelay.debug(
            "fake text spawned id=" +
                textEntityId +
                " pos=" +
                worldPos +
                " align=" +
                drawable.hAlign() +
                "/" +
                drawable.vAlign() +
                " hitbox=" +
                measuredHitbox[0] +
                "x" +
                measuredHitbox[1] +
                " text=\"" +
                lastRendered +
                "\""
        );
    }

    private int spawnText(Vector pos, String text) {
        int id = nextId();
        PacketUtils.send(
            viewer,
            new WrapperPlayServerSpawnEntity(
                id,
                Optional.of(UUID.randomUUID()),
                EntityTypes.TEXT_DISPLAY,
                new Vector3d(pos.getX(), pos.getY(), pos.getZ()),
                0f,
                0f,
                0f,
                0,
                Optional.empty()
            )
        );

        float scale = canvas.scaleForHeightPx(drawable.heightPx());
        PacketUtils.send(viewer, new WrapperPlayServerEntityMetadata(id, textMetadata(text, scale)));
        return id;
    }

    private List<EntityData<?>> textMetadata(String text, float scale) {
        PixelaySettings.Text style = Pixelay.settings().text();
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0)); // entity flags
        data.add(new EntityData<>(idx.transformationStartDelta(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.transformationDuration(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.positionInterpolationDuration(), EntityDataTypes.INT, 0));
        data.add(new EntityData<>(idx.translation(), EntityDataTypes.VECTOR3F, new Vector3f(0f, 0f, 0f)));
        data.add(new EntityData<>(idx.scale(), EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)));

        org.joml.Quaternionf rot = canvas.contentRotation();
        data.add(
            new EntityData<>(
                idx.leftRotation(),
                EntityDataTypes.QUATERNION,
                new Quaternion4f(rot.x, rot.y, rot.z, rot.w)
            )
        );
        data.add(new EntityData<>(idx.rightRotation(), EntityDataTypes.QUATERNION, new Quaternion4f(0f, 0f, 0f, 1f)));
        data.add(new EntityData<>(idx.billboard(), EntityDataTypes.BYTE, BILLBOARD_FIXED));
        data.add(new EntityData<>(idx.viewRange(), EntityDataTypes.FLOAT, style.viewRange()));
        data.add(new EntityData<>(idx.text(), EntityDataTypes.ADV_COMPONENT, CanvasLayout.component(drawable, text)));
        data.add(new EntityData<>(idx.lineWidth(), EntityDataTypes.INT, CanvasLayout.CLIENT_LINE_WIDTH));
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

    @Override
    public void despawn() {
        if (textEntityId != 0) {
            PacketUtils.send(viewer, new WrapperPlayServerDestroyEntities(new int[] { textEntityId }));
        }

        textEntityId = 0;
        background.remove();
        if (canvas != null) {
            CanvasClicks.unregister(canvas, viewer, clickTarget);
        }
    }

    /** Teleports the rendered text to a world position (used by moving elements like a cursor). */
    @Override
    public void move(Vector worldPos) {
        if (textEntityId == 0) {
            return;
        }

        // Moved elements (like the cursor) are not refreshed by layouts: keep their background here.
        syncBackground();
        // The translation is kept, so the entity goes where the visible text lands on worldPos.
        entityPos = worldPos.clone().subtract(translation);
        PacketUtils.send(
            viewer,
            new WrapperPlayServerEntityTeleport(
                textEntityId,
                new Vector3d(entityPos.getX(), entityPos.getY(), entityPos.getZ()),
                0f,
                0f,
                false
            )
        );
    }

    @Override
    public boolean isValid() {
        // Fake entities have no server state; assume valid until explicitly despawned.
        return textEntityId != 0;
    }

    @Override
    public void refresh() {
        if (textEntityId == 0) {
            return;
        }

        String rendered = drawable.renderText();

        if (
            rendered.equals(lastRendered) &&
            drawable.layoutVersion() == lastLayoutVersion &&
            drawable.lineWidth() == lastLineWidth
        ) {
            syncBackground();
            return;
        }

        lastRendered = rendered;
        lastLayoutVersion = drawable.layoutVersion();
        lastLineWidth = drawable.lineWidth();
        measuredHitbox = CanvasLayout.hitboxSizePx(drawable, rendered);
        double[] pos = CanvasLayout.displayPosition(drawable, rendered);
        translation = canvas.pixelToWorld(pos[0], pos[1], drawable.textDepth()).toVector().subtract(entityPos);

        // Text and position go in ONE metadata packet: a separate teleport + metadata pair lets the
        // client render a frame with the new text at the old position (visible as a jerk).
        PacketUtils.send(
            viewer,
            new WrapperPlayServerEntityMetadata(
                textEntityId,
                List.of(
                    new EntityData<>(
                        Pixelay.settings().packets().metadata().text(),
                        EntityDataTypes.ADV_COMPONENT,
                        CanvasLayout.component(drawable, rendered)
                    ),
                    new EntityData<>(
                        Pixelay.settings().packets().metadata().translation(),
                        EntityDataTypes.VECTOR3F,
                        new Vector3f((float) translation.getX(), (float) translation.getY(), (float) translation.getZ())
                    ),
                    // Pixelay breaks the lines itself (written into the text): the client must never wrap.
                    new EntityData<>(
                        Pixelay.settings().packets().metadata().lineWidth(),
                        EntityDataTypes.INT,
                        CanvasLayout.CLIENT_LINE_WIDTH
                    )
                )
            )
        );
        syncBackground();
    }

    @Override
    public double[] measuredHitboxPx() {
        return measuredHitbox;
    }

    @Override
    public Player viewer() {
        return viewer;
    }

    private static int nextId() {
        return nextFakeEntityId();
    }

    /**
     * Shared source of fake ids (client entities should not have overlapping ids across renderers).
     * Ids start at {@code packets.fake-entity-id-base}, high enough to never collide with real
     * server entity ids, which grow from 0.
     */
    public static int nextFakeEntityId() {
        if (!idBaseApplied) {
            synchronized (ENTITY_ID) {
                if (!idBaseApplied) {
                    ENTITY_ID.set(Pixelay.settings().packets().fakeEntityIdBase());
                    idBaseApplied = true;
                }
            }
        }

        return ENTITY_ID.getAndIncrement();
    }

    /** Background panel: the debug highlight (blue / red / orange) while debugging, the configured one otherwise. */
    private void syncBackground() {
        background.sync(
            canvas,
            drawable,
            measuredHitbox,
            TextBackground.colorOf(drawable, LayoutDebug.isEnabled(viewer))
        );
    }

    private boolean clickAt(double px, double py, Player player) {
        double[] hitbox = measuredHitbox;
        if (drawable.onClick() == null || hitbox == null || !CanvasLayout.contains(drawable, hitbox, px, py)) {
            return false;
        }
        drawable.onClick().accept(player);
        return true;
    }
}
