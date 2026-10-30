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
 * Renders a 3D model on a canvas, such as an item ({@link CanvasItem}) or a block ({@link CanvasBlock}).
 * Fits the model into a canvas pixel box and flattens it onto its depth layer.
 * This prevents the model from clipping through layers in front of it.
 * The anchor coordinates and alignment match text positioning.
 * <p>
 * Supports three rendering modes: server entities, per-player entities, and packet-only fake entities.
 * Content comes from a supplier and updates on refresh.
 *
 * @param <T> the drawn content: an item stack or a block state
 */
public abstract class CanvasModel<T> implements LayoutComponent {

    /** Packed block and sky light 15/15 for full brightness. */
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
    /** Bounding clip rectangle for layout drawing, or {@code null} outside layouts. */
    private volatile Rect clip;
    private volatile double rotateDeg;
    private volatile double rotateXDeg;
    private volatile double rotateYDeg;

    private Canvas canvas;
    private Display display;
    private int fakeId;
    /** Base world position of the entity. Movements use translation transformations. */
    private Vector entityPos;
    private T lastContent;
    private Transformation lastTransformation;

    /**
     * @param viewer the single player who sees the model, or {@code null} for all players
     * @param fake true if the model is packet-only, false otherwise
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

    /** @return display entity class for server-side rendering */
    protected abstract Class<? extends Display> displayClass();

    /** @return display entity type for packet rendering */
    protected abstract EntityType packetType();

    /** Applies content and settings to a server display entity. */
    protected abstract void applyContent(Display display, T content);

    /** @return metadata list that displays content on a packet-only entity */
    protected abstract List<EntityData<?>> contentData(T content);

    /**
     * Calculates the model origin offset in blocks relative to the bottom-left box corner.
     */
    protected abstract Vector3f modelOffset(float width, float height, float thickness);

    /** Model rotation applied before box scaling so the front faces outward. */
    protected abstract Quaternionf modelRotation();

    /** @return empty content instance when supplier returns null */
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
            // Send content and position in one packet to prevent flickering at old positions.
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
        // Fake entities have no server state and stay valid until despawn.
        return fake ? fakeId != 0 : display != null && display.isValid();
    }

    /** Binds a viewer and switches the model to packet-only rendering if unassigned. */
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
     * Pins the anchor point to specified box edges.
     * For example, {@code align(HAlign.LEFT, VAlign.TOP)} sets {@code (pixelX, pixelY)} as the top-left corner.
     */
    @SuppressWarnings("unchecked")
    public <S extends CanvasModel<T>> S align(HAlign hAlign, VAlign vAlign) {
        this.hAlign = hAlign;
        this.vAlign = vAlign;
        return (S) this;
    }

    /** @return true if the pointer hovers over this model in its layout (CSS {@code :hover}) */
    public boolean isHovered() {
        return hovered;
    }

    /** @return natural width requested by the model in canvas pixels */
    public double naturalWidthPx() {
        return naturalWidthPx;
    }

    public double naturalHeightPx() {
        return naturalHeightPx;
    }

    /** Sets the model box bounds from layout calculations with top-left at {@code (x, y)}. */
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
        // The next refresh applies the layer step in the transformation.
        layerStep = step;
    }

    @Override
    @ApiStatus.Internal
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    /**
     * Sets the bounding clip rectangle.
     * Models render fully when partially inside the clip and disappear when completely outside.
     */
    @Override
    @ApiStatus.Internal
    public void setClip(Rect clip) {
        this.clip = clip;
    }

    /** @return true if the model box is entirely outside the clip area, false otherwise */
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

    /** @return world location of the bottom-left box corner on the model layer */
    private Location cornerWorld() {
        double left = pixelX + hAlign.horizontalOffset(widthPx) - widthPx / 2;
        double bottom = pixelY + vAlign.bottomOffset(heightPx);
        return canvas.pixelToWorld(left, bottom, Layers.at(layerStep, viewer));
    }

    /**
     * Calculates the model transformation fitted into the target box.
     */
    private Transformation transformation() {
        CanvasResolution res = canvas.resolution();
        float width = (float) ((widthPx * res.blockWidth()) / res.pixelWidth());
        float height = (float) ((heightPx * res.blockHeight()) / res.pixelHeight());
        // Use a half-step thickness to keep the model between adjacent layers.
        float thickness = Layers.at(0.5, viewer);
        Quaternionf rotation = canvas.contentRotation();
        Vector corner = cornerWorld().toVector().subtract(entityPos);
        Vector3f translation = rotation
            .transform(modelOffset(width, height, thickness))
            .add((float) corner.getX(), (float) corner.getY(), (float) corner.getZ());
        // Scale to zero when clipped away so content restores without respawning entities.
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

    /** @return initial metadata list sent at spawn */
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
