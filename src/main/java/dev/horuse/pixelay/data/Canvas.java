package dev.horuse.pixelay.data;

import dev.horuse.pixelay.Pixelay;
import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A flat pixel plane placed in the world — the single source of truth for positioning everything
 * drawn on it.
 *
 * <p>Coordinate system: pixel origin {@code (0,0)} is the top-left corner, {@code x} grows right,
 * {@code y} grows down (like an image editor). The plane is centered on {@link #center} and rotated
 * by {@link #yawDegrees} about the Y axis, so every component derives its world transform from this
 * one object; rotating or moving the canvas moves everything coherently.
 */
public class Canvas {

    /**
     * World height (blocks) of a single text line at scale {@code 1.0}. The client renders text
     * display font pixels at {@code 0.025} blocks each (40 px per block), so a 9px font line is
     * {@code 9 * 0.025 = 0.225} blocks, and "N px" text uses {@code scale = (N/48) / 0.225}.
     */
    public static final float TEXT_HEIGHT_PER_SCALE = 0.225f;

    private final Location center;
    private final float yawDegrees;
    private final float pitchDegrees;
    private final float depthOffset;
    private final CanvasResolution resolution;
    private final double paddingPx;

    private final Quaternionf rotation;
    private final Quaternionf contentRotation;
    private final Vector3f right;
    private final Vector3f up;
    private final Vector3f normal;
    private volatile boolean worldClicks;

    public Canvas(Location center, float yawDegrees, CanvasResolution resolution) {
        this(center, yawDegrees, 0f, 0f, 0f, resolution);
    }

    public Canvas(
        Location center,
        float yawDegrees,
        float pitchDegrees,
        float contentFacingYawOffset,
        float depthOffset,
        CanvasResolution resolution
    ) {
        this(center, yawDegrees, pitchDegrees, contentFacingYawOffset, depthOffset, resolution, 0);
    }

    /**
     * @param contentFacingYawOffset extra yaw (degrees) applied only to component orientation, to
     *     align text/icons with a background whose model has a baked facing
     * @param depthOffset global outward shift (blocks) applied to every component, e.g. when the
     *     background texture itself is pushed forward off the wall
     * @param paddingPx inset (canvas px) kept clear at every edge; {@link #anchorX}/{@link
     *     #anchorY} return coordinates inside it, so anchor-placed content never touches the
     *     borders
     */
    public Canvas(
        Location center,
        float yawDegrees,
        float pitchDegrees,
        float contentFacingYawOffset,
        float depthOffset,
        CanvasResolution resolution,
        double paddingPx
    ) {
        this.center = center.clone();
        this.yawDegrees = yawDegrees;
        this.pitchDegrees = pitchDegrees;
        this.depthOffset = depthOffset;
        this.resolution = resolution;
        this.paddingPx = paddingPx;

        this.rotation = new Quaternionf()
            .rotateY((float) Math.toRadians(yawDegrees))
            .rotateX((float) Math.toRadians(pitchDegrees));
        this.contentRotation = new Quaternionf(rotation).rotateY((float) Math.toRadians(contentFacingYawOffset));
        this.right = rotation.transform(new Vector3f(1f, 0f, 0f));
        this.up = rotation.transform(new Vector3f(0f, 1f, 0f));
        this.normal = rotation.transform(new Vector3f(0f, 0f, 1f));
        warnIfPixelsNotSquare(resolution);
    }

    /**
     * Text is scaled by the pixel height, while boxes, positions and clicks use both pixel sizes: with
     * pixels wider than tall (or the other way round) texts no longer fill their boxes.
     */
    private static void warnIfPixelsNotSquare(CanvasResolution resolution) {
        double pixelWidth = resolution.blockWidth() / resolution.pixelWidth();
        double pixelHeight = resolution.blockHeight() / resolution.pixelHeight();
        if (Math.abs(pixelWidth / pixelHeight - 1) > 0.01) {
            Pixelay.plugin()
                .getLogger()
                .warning(
                    "Canvas " +
                        resolution +
                        " has non-square pixels (" +
                        String.format(java.util.Locale.ROOT, "%.3f", pixelWidth / pixelHeight) +
                        ":1): keep pixelWidth/pixelHeight equal to blockWidth/blockHeight, or texts will not match their boxes"
                );
        }
    }

    /**
     * Converts a pixel coordinate into the world spawn {@link Location}, pushed out along the plane
     * normal by {@code text.normal-offset} so components never Z-fight with the background.
     */
    public Location pixelToWorld(double pixelX, double pixelY) {
        return pixelToWorld(pixelX, pixelY, Pixelay.settings().text().normalOffset());
    }

    /**
     * Converts a pixel coordinate into the world spawn {@link Location} with an explicit outward
     * offset along the plane normal.
     */
    public Location pixelToWorld(double pixelX, double pixelY, float normalOffset) {
        double localRight =
            ((pixelX - resolution.pixelWidth() / 2.0) / resolution.pixelWidth()) * resolution.blockWidth();
        double localUp =
            (-(pixelY - resolution.pixelHeight() / 2.0) / resolution.pixelHeight()) * resolution.blockHeight();
        double outward = depthOffset + normalOffset;

        double x = center.getX() + right.x * localRight + up.x * localUp + normal.x * outward;
        double y = center.getY() + right.y * localRight + up.y * localUp + normal.y * outward;
        double z = center.getZ() + right.z * localRight + up.z * localUp + normal.z * outward;

        return new Location(center.getWorld(), x, y, z);
    }

    /**
     * @return world height in blocks for a pixel height on this plane
     */
    public float blocksForHeightPx(double heightPx) {
        return (float) ((heightPx / resolution.pixelHeight()) * resolution.blockHeight());
    }

    /**
     * @return the {@link org.bukkit.entity.TextDisplay} scale that renders text {@code heightPx}
     *     tall
     */
    public float scaleForHeightPx(double heightPx) {
        return blocksForHeightPx(heightPx) / TEXT_HEIGHT_PER_SCALE;
    }

    /**
     * @return a fresh copy of the plane rotation (safe to mutate)
     */
    public Quaternionf rotation() {
        return new Quaternionf(rotation);
    }

    /**
     * @return the rotation components should render with — the plane rotation plus any content
     *     facing offset (used to align text/icons with a background model's baked facing)
     */
    public Quaternionf contentRotation() {
        return new Quaternionf(contentRotation);
    }

    /**
     * @return a copy of the plane's local up axis (world space)
     */
    public Vector3f up() {
        return new Vector3f(up);
    }

    /**
     * @return a copy of the plane's local right axis (world space)
     */
    public Vector3f right() {
        return new Vector3f(right);
    }

    /**
     * @return a copy of the plane's outward normal (world space)
     */
    public Vector3f normal() {
        return new Vector3f(normal);
    }

    /**
     * Lets players click this canvas in the world (off by default): invisible click tiles are laid over
     * its plane while something clickable is spawned, and the clicked pixel comes from the player's look
     * ray. Leave it off for canvases with their own input, such as a full-screen screen with a cursor:
     * clicks then come only through {@link dev.horuse.pixelay.clicks.CanvasClicks#click}. Set it before
     * spawning anything clickable.
     */
    public Canvas worldClicks(boolean enabled) {
        this.worldClicks = enabled;
        return this;
    }

    public boolean worldClicks() {
        return worldClicks;
    }

    public Location center() {
        return center.clone();
    }

    public float yawDegrees() {
        return yawDegrees;
    }

    public float pitchDegrees() {
        return pitchDegrees;
    }

    public CanvasResolution resolution() {
        return resolution;
    }

    public double paddingPx() {
        return paddingPx;
    }

    /**
     * @return X of the anchor line for the given horizontal alignment (padding-aware)
     */
    public double anchorX(HAlign align) {
        return switch (align) {
            case LEFT -> paddingPx;
            case CENTER -> resolution.pixelWidth() / 2.0;
            case RIGHT -> resolution.pixelWidth() - paddingPx;
        };
    }

    /**
     * @return Y of the anchor line for the given vertical alignment (padding-aware)
     */
    public double anchorY(VAlign align) {
        return switch (align) {
            case TOP -> paddingPx;
            case MIDDLE -> resolution.pixelHeight() / 2.0;
            case BOTTOM -> resolution.pixelHeight() - paddingPx;
        };
    }

    /**
     * @return the anchor pixel {@code {x, y}} for the given alignment (padding-aware)
     */
    public double[] anchorPoint(HAlign x, VAlign y) {
        return new double[] { anchorX(x), anchorY(y) };
    }

    /**
     * Projects a ray (origin + direction) onto the plane, returning the raw pixel coordinates
     * {@code {pixelX, pixelY}} of the hit even when they fall outside the canvas bounds, or {@code
     * null} if the ray is parallel to the plane or points away. Used to map a player's look
     * direction to a canvas position (e.g. cursor hover), letting the caller clamp to the edges or
     * treat an off-screen hit as "not looking at the canvas".
     */
    public double[] rayPlanePixel(org.bukkit.util.Vector origin, org.bukkit.util.Vector dir) {
        Vector3f o = new Vector3f((float) origin.getX(), (float) origin.getY(), (float) origin.getZ());
        Vector3f d = new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ());
        float denom = d.dot(normal);

        if (Math.abs(denom) < 1.0e-6f) {
            return null;
        }

        // The plane the components are drawn on: pushed out by depthOffset and text.normal-offset.
        float outward = depthOffset + Pixelay.settings().text().normalOffset();
        Vector3f c = new Vector3f((float) center.getX(), (float) center.getY(), (float) center.getZ()).fma(
            outward,
            normal
        );
        float t = new Vector3f(c).sub(o).dot(normal) / denom;

        if (t < 0) {
            return null;
        }

        Vector3f hit = new Vector3f(o).fma(t, d).sub(c);
        double px =
            (hit.dot(right) / resolution.blockWidth()) * resolution.pixelWidth() + resolution.pixelWidth() / 2.0;
        double py =
            (-hit.dot(up) / resolution.blockHeight()) * resolution.pixelHeight() + resolution.pixelHeight() / 2.0;
        return new double[] { px, py };
    }

    /**
     * @return the pixel coordinates {@code {pixelX, pixelY}} of the ray's hit on the canvas plane,
     *     or {@code null} if the ray is parallel to the plane, points away, or hits outside the
     *     canvas bounds.
     */
    public double[] pixelFromRay(org.bukkit.util.Vector origin, org.bukkit.util.Vector dir) {
        double[] hit = rayPlanePixel(origin, dir);
        if (hit == null) {
            return null;
        }

        if (hit[0] < 0 || hit[0] > resolution.pixelWidth() || hit[1] < 0 || hit[1] > resolution.pixelHeight()) {
            return null;
        }

        return hit;
    }
}
