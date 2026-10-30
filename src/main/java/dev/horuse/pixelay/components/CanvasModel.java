package dev.horuse.pixelay.components;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.config.PixelaySettings;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.CanvasResolution;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.VAlign;
import dev.horuse.pixelay.utils.DisplayUtils;
import dev.horuse.pixelay.utils.FakeEntityIds;
import dev.horuse.pixelay.utils.PacketUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A 3D model on a canvas — an item ({@link CanvasItem}) or a block ({@link CanvasBlock}) — fitted into a box of
 * canvas px and pressed flat onto its depth layer, so it looks like a picture on the canvas and never pokes
 * through the layers in front of it. The anchor point and alignment work as for texts: {@code align(LEFT, TOP)}
 * makes {@code (pixelX, pixelY)} the top-left corner of the box.
 * <p>
 * Drawn with a real display entity seen by everyone, one seen by a single player ({@code perPlayer}), or a
 * packet-only one of a single player ({@code fake}). The content comes from a supplier and is re-read on every
 * refresh. Clicks and hover go through a layout box: {@code node(item).onClick(...)}.
 *
 * @param <T> the drawn content: an item stack or a block state
 */
public abstract class CanvasModel<T> implements LayoutComponent {

    /** Packed block/sky light 15/15 — forces full brightness. */
    private static final int FULL_BRIGHTNESS = 0xF000F0;
    private static final int NO_BRIGHTNESS_OVERRIDE = -1;
    private static final byte BILLBOARD_FIXED = 0;

    private Player viewer;
    private boolean fake;
    private final Supplier<T> content;
    private final double naturalWidthPx;
    private final double naturalHeightPx;
    private double pixelX;
    private double pixelY;
    private double widthPx;
    private double heightPx;
    private HAlign hAlign = HAlign.CENTER;
    private VAlign vAlign = VAlign.BOTTOM;
    private volatile int layerStep = Layers.DEFAULT_TEXT_BACKGROUND_STEP;
    private volatile boolean hovered;
    /** Where a layout lets the model draw, or {@code null} outside layouts (the canvas then). */
    private volatile Rect clip;
    private volatile double rotateDeg;
    private volatile double rotateXDeg;
    private volatile double rotateYDeg;

    private Canvas canvas;
    private Display display;
    private int fakeId;
    /** Where the entity stands; it is moved only through its translation, like the fake texts. */
    private Vector entityPos;
    private T lastContent;
    private Transformation lastTransformation;

    /**
     * @param viewer the only player who sees the model, or {@code null} for everyone
     * @param fake whether the model is packet-only (requires a viewer)
     */
    protected CanvasModel(
        Player viewer,
        boolean fake,
        double pixelX,
        double pixelY,
        double widthPx,
        double heightPx,
        Supplier<T> content
    ) {
        if (fake && viewer == null) {
            throw new IllegalArgumentException("A fake model needs a viewer");
        }
        this.viewer = viewer;
        this.fake = fake;
        this.content = content;
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.naturalWidthPx = widthPx;
        this.naturalHeightPx = heightPx;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    /** The display entity class drawn for everyone or one player. */
    protected abstract Class<? extends Display> displayClass();

    /** The same display entity type for packets. */
    protected abstract EntityType packetType();

    /** Puts the content into a server display entity (and anything else the entity type needs). */
    protected abstract void applyContent(Display display, T content);

    /** The metadata showing the content on a packet-only entity. */
    protected abstract List<EntityData<?>> contentData(T content);

    /**
     * Where the model's origin goes inside its box, in blocks along the canvas right, up and out from the box's
     * bottom-left corner on its layer.
     */
    protected abstract Vector3f modelOffset(float width, float height, float thickness);

    /** Rotation of the model before it is scaled to its box, so that its front faces out of the canvas. */
    protected abstract Quaternionf modelRotation();

    /** {@code null} content draws nothing. */
    protected abstract T empty();

    @Override
    public void spawn(Canvas canvas) {
        if (isValid()) {
            return;
        }
        this.canvas = canvas;
        T now = currentContent();
        Location at = cornerWorld();
        entityPos = at.toVector();
        Transformation transformation = transformation();
        if (fake) {
            fakeId = FakeEntityIds.next();
            PacketUtils.spawnEntity(viewer, fakeId, packetType(), at.getX(), at.getY(), at.getZ());
            List<EntityData<?>> data = new ArrayList<>(staticData());
            data.addAll(transformationData(transformation));
            data.addAll(contentData(now));
            PacketUtils.send(viewer, new WrapperPlayServerEntityMetadata(fakeId, data));
        } else {
            display = at.getWorld().spawn(at, displayClass(), entity -> {
                DisplayUtils.tag(entity);
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setViewRange(Pixelay.settings().text().viewRange());
                if (Pixelay.settings().text().fullBrightness()) {
                    entity.setBrightness(new Display.Brightness(15, 15));
                }
                entity.setTransformation(transformation);
                applyContent(entity, now);
                if (viewer != null) {
                    entity.setVisibleByDefault(false);
                }
            });
            if (viewer != null) {
                viewer.showEntity(Pixelay.plugin(), display);
            }
        }
        lastContent = now;
        lastTransformation = transformation;
    }

    @Override
    public void refresh() {
        if (!isValid()) {
            return;
        }
        T now = currentContent();
        Transformation transformation = transformation();
        boolean moved = !transformation.equals(lastTransformation);
        boolean changed = !Objects.equals(now, lastContent);
        if (!moved && !changed) {
            return;
        }
        if (fake) {
            // Content and position in one packet: the new content must not blink at the old position.
            List<EntityData<?>> data = new ArrayList<>();
            if (moved) {
                data.addAll(transformationData(transformation));
            }
            if (changed) {
                data.addAll(contentData(now));
            }
            PacketUtils.send(viewer, new WrapperPlayServerEntityMetadata(fakeId, data));
        } else {
            if (moved) {
                display.setInterpolationDuration(transformationDuration);
                display.setInterpolationDelay(0);
                display.setTransformation(transformation);
            }
            if (changed) {
                applyContent(display, now);
            }
        }
        lastContent = now;
        lastTransformation = transformation;
    }

    @Override
    public void despawn() {
        if (fakeId != 0) {
            PacketUtils.destroy(viewer, fakeId);
            fakeId = 0;
        }
        if (display != null) {
            display.remove();
            display = null;
        }
    }

    @Override
    public boolean isValid() {
        // Fake entities have no server state; valid until explicitly despawned.
        return fake ? fakeId != 0 : display != null && display.isValid();
    }

    /** Switches this model to packet-only rendering for {@code viewer} if it currently has no viewer. */
    public void bindViewer(Player viewer) {
        if (viewer != null && this.viewer == null) {
            this.viewer = viewer;
            this.fake = true;
        }
    }

    @Override
    public Player viewer() {
        return viewer;
    }

    /**
     * Pins the anchor point to the given edges of the model's box, as for texts: {@code align(HAlign.LEFT,
     * VAlign.TOP)} makes {@code (pixelX, pixelY)} its top-left corner.
     */
    @SuppressWarnings("unchecked")
    public <S extends CanvasModel<T>> S align(HAlign hAlign, VAlign vAlign) {
        this.hAlign = hAlign;
        this.vAlign = vAlign;
        return (S) this;
    }

    /** Whether the pointer is over this model's box in its layout (CSS {@code :hover}). */
    public boolean isHovered() {
        return hovered;
    }

    /** The size the model asks for in a layout, in canvas px. */
    public double naturalWidthPx() {
        return naturalWidthPx;
    }

    public double naturalHeightPx() {
        return naturalHeightPx;
    }

    /** Set by layouts: the model's box, its top-left corner at {@code (x, y)}. */
    @ApiStatus.Internal
    public void place(double x, double y, double width, double height) {
        this.pixelX = x;
        this.pixelY = y;
        this.widthPx = width;
        this.heightPx = height;
        this.hAlign = HAlign.LEFT;
        this.vAlign = VAlign.TOP;
    }

    @ApiStatus.Internal
    public void setVisualRotation(double rotateDeg, double rotateXDeg, double rotateYDeg) {
        this.rotateDeg = rotateDeg;
        this.rotateXDeg = rotateXDeg;
        this.rotateYDeg = rotateYDeg;
    }

    @Override
    @ApiStatus.Internal
    public void setLayerStep(int step) {
        // Picked up by the next refresh through the transformation.
        layerStep = step;
    }

    @Override
    @ApiStatus.Internal
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    /**
     * A model can not be cut like a text: it disappears once its box is entirely outside the clip (or the
     * canvas), and is drawn whole while any part of it is inside. Picked up by the next refresh.
     */
    @Override
    @ApiStatus.Internal
    public void setClip(Rect clip) {
        this.clip = clip;
    }

    /** Whether the model's box is entirely outside where it may draw. */
    private boolean clippedAway() {
        CanvasResolution res = canvas.resolution();
        Rect visible = new Rect(0, 0, res.pixelWidth(), res.pixelHeight());
        if (clip != null) {
            visible = visible.intersect(clip);
        }
        double left = pixelX + hAlign.horizontalOffset(widthPx) - widthPx / 2;
        double top = pixelY + vAlign.bottomOffset(heightPx) - heightPx;
        return visible.intersect(new Rect(left, top, widthPx, heightPx)).isEmpty();
    }

    private T currentContent() {
        T value = content.get();
        return value != null ? value : empty();
    }

    /** The bottom-left corner of the box on the model's layer. */
    private Location cornerWorld() {
        double left = pixelX + hAlign.horizontalOffset(widthPx) - widthPx / 2;
        double bottom = pixelY + vAlign.bottomOffset(heightPx);
        return canvas.pixelToWorld(left, bottom, Layers.at(layerStep, viewer));
    }

    /**
     * The model fitted into its box. The entity never moves (its yaw and pitch are 0, so the translation is in
     * world axes): the box corner relative to it, plus the model's own offset turned with the canvas.
     */
    private Transformation transformation() {
        CanvasResolution res = canvas.resolution();
        float width = (float) ((widthPx * res.blockWidth()) / res.pixelWidth());
        float height = (float) ((heightPx * res.blockHeight()) / res.pixelHeight());
        // Half a step: the flattened model stays between its own layer and the next one.
        float thickness = Layers.at(0.5, viewer);
        Quaternionf rotation = canvas.contentRotation();
        Vector corner = cornerWorld().toVector().subtract(entityPos);
        Vector3f translation = rotation
            .transform(modelOffset(width, height, thickness))
            .add((float) corner.getX(), (float) corner.getY(), (float) corner.getZ());
        // Clipped away: scaled to nothing, so it comes back in place without a respawn.
        Vector3f scale = clippedAway() ? new Vector3f() : new Vector3f(width, height, thickness);
        Quaternionf rightRot = modelRotation();
        if (rotateDeg != 0.0) {
            rightRot.rotateZ((float) Math.toRadians(-rotateDeg));
        }
        if (rotateXDeg != 0.0) {
            rightRot.rotateX((float) Math.toRadians(rotateXDeg));
        }
        if (rotateYDeg != 0.0) {
            rightRot.rotateY((float) Math.toRadians(rotateYDeg));
        }
        return new Transformation(translation, rotation, scale, rightRot);
    }

    /** Metadata set once at spawn. */
    private List<EntityData<?>> staticData() {
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        PixelaySettings.Text style = Pixelay.settings().text();
        return List.of(
            new EntityData<>(idx.transformationStartDelta(), EntityDataTypes.INT, 0),
            new EntityData<>(idx.transformationDuration(), EntityDataTypes.INT, 0),
            new EntityData<>(idx.positionInterpolationDuration(), EntityDataTypes.INT, 0),
            new EntityData<>(idx.billboard(), EntityDataTypes.BYTE, BILLBOARD_FIXED),
            new EntityData<>(idx.viewRange(), EntityDataTypes.FLOAT, style.viewRange()),
            new EntityData<>(
                idx.brightnessOverride(),
                EntityDataTypes.INT,
                style.fullBrightness() ? FULL_BRIGHTNESS : NO_BRIGHTNESS_OVERRIDE
            )
        );
    }

    private int transformationDuration = 0;

    @Override
    public void setTransformationDuration(int ticks) {
        this.transformationDuration = Math.max(0, ticks);
    }

    private List<EntityData<?>> transformationData(Transformation t) {
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        return List.of(
            new EntityData<>(idx.transformationStartDelta(), EntityDataTypes.INT, 0),
            new EntityData<>(idx.transformationDuration(), EntityDataTypes.INT, transformationDuration),
            new EntityData<>(idx.translation(), EntityDataTypes.VECTOR3F, vector(t.getTranslation())),
            new EntityData<>(idx.scale(), EntityDataTypes.VECTOR3F, vector(t.getScale())),
            new EntityData<>(idx.leftRotation(), EntityDataTypes.QUATERNION, quaternion(t.getLeftRotation())),
            new EntityData<>(idx.rightRotation(), EntityDataTypes.QUATERNION, quaternion(t.getRightRotation()))
        );
    }

    private static com.github.retrooper.packetevents.util.Vector3f vector(Vector3f v) {
        return new com.github.retrooper.packetevents.util.Vector3f(v.x, v.y, v.z);
    }

    private static Quaternion4f quaternion(Quaternionf q) {
        return new Quaternion4f(q.x, q.y, q.z, q.w);
    }
}
