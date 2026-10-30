package dev.horuse.pixelay.data;

import dev.horuse.pixelay.Pixelay;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Defines a flat pixel plane in the world.
 * This plane serves as the source of truth for component coordinates.
 * The coordinate system has its origin at the top-left corner.
 * The X axis extends to the right and the Y axis extends downward.
 */
public class Canvas {

    /** World height in blocks of a single text line at scale 1.0. */
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
     * @param contentFacingYawOffset yaw offset in degrees for component orientation
     * @param depthOffset outward depth offset in blocks from the canvas plane
     * @param paddingPx inner padding distance in canvas pixels
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

    /** Validates that pixel aspect ratio matches block dimensions. */
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
                        String.format(Locale.ROOT, "%.3f", pixelWidth / pixelHeight) +
                        ":1): keep pixelWidth/pixelHeight equal to blockWidth/blockHeight, or texts will not match their boxes"
                );
        }
    }

    /** Converts a pixel coordinate to a world location on the text layer. */
    public Location pixelToWorld(double pixelX, double pixelY) {
        return pixelToWorld(pixelX, pixelY, Layers.text());
    }

    /** Converts a pixel coordinate to a world location with custom layer depth. */
    public Location pixelToWorld(double pixelX, double pixelY, float layerDepth) {
        double localRight =
            ((pixelX - resolution.pixelWidth() / 2.0) / resolution.pixelWidth()) * resolution.blockWidth();
        double localUp =
            (-(pixelY - resolution.pixelHeight() / 2.0) / resolution.pixelHeight()) * resolution.blockHeight();
        double outward = depthOffset + layerDepth;

        double x = center.getX() + right.x * localRight + up.x * localUp + normal.x * outward;
        double y = center.getY() + right.y * localRight + up.y * localUp + normal.y * outward;
        double z = center.getZ() + right.z * localRight + up.z * localUp + normal.z * outward;

        return new Location(center.getWorld(), x, y, z);
    }

    /** Converts pixel coordinates to a world location rotated around a pivot point. */
    public Location rotatedPixelToWorld(
        double pixelX,
        double pixelY,
        float layerDepth,
        double rotateDeg,
        double rotateXDeg,
        double rotateYDeg,
        double pivotX,
        double pivotY
    ) {
        if (Math.abs(rotateDeg) < 1e-4 && Math.abs(rotateXDeg) < 1e-4 && Math.abs(rotateYDeg) < 1e-4) {
            return pixelToWorld(pixelX, pixelY, layerDepth);
        }
        double px = Double.isNaN(pivotX) ? pixelX : pivotX;
        double py = Double.isNaN(pivotY) ? pixelY : pivotY;

        double deltaX = pixelX - px;
        double deltaY = pixelY - py;
        double localRight = (deltaX / resolution.pixelWidth()) * resolution.blockWidth();
        double localUp = (-deltaY / resolution.pixelHeight()) * resolution.blockHeight();

        Quaternionf qLocal = new Quaternionf();
        if (Math.abs(rotateDeg) >= 1e-4) {
            qLocal.rotateZ((float) Math.toRadians(-rotateDeg));
        }
        if (Math.abs(rotateXDeg) >= 1e-4) {
            qLocal.rotateX((float) Math.toRadians(rotateXDeg));
        }
        if (Math.abs(rotateYDeg) >= 1e-4) {
            qLocal.rotateY((float) Math.toRadians(rotateYDeg));
        }

        Vector3f vRot = qLocal.transform(new Vector3f((float) localRight, (float) localUp, 0f));

        Location pivotWorld = pixelToWorld(px, py, layerDepth);
        double x = pivotWorld.getX() + right.x * vRot.x + up.x * vRot.y + normal.x * vRot.z;
        double y = pivotWorld.getY() + right.y * vRot.x + up.y * vRot.y + normal.y * vRot.z;
        double z = pivotWorld.getZ() + right.z * vRot.x + up.z * vRot.y + normal.z * vRot.z;

        return new Location(pivotWorld.getWorld(), x, y, z);
    }

    /**
     * @return world height in blocks for a given height in pixels
     */
    public float blocksForHeightPx(double heightPx) {
        return (float) ((heightPx / resolution.pixelHeight()) * resolution.blockHeight());
    }

    /**
     * @return text display scale factor for a given height in pixels
     */
    public float scaleForHeightPx(double heightPx) {
        return blocksForHeightPx(heightPx) / TEXT_HEIGHT_PER_SCALE;
    }

    /**
     * @return a copy of the canvas plane rotation
     */
    public Quaternionf rotation() {
        return new Quaternionf(rotation);
    }

    /**
     * @return the rotation applied to canvas components
     */
    public Quaternionf contentRotation() {
        return new Quaternionf(contentRotation);
    }

    /**
     * @return a copy of the canvas up direction vector
     */
    public Vector3f up() {
        return new Vector3f(up);
    }

    /**
     * @return a copy of the canvas right direction vector
     */
    public Vector3f right() {
        return new Vector3f(right);
    }

    /**
     * @return a copy of the canvas outward normal vector
     */
    public Vector3f normal() {
        return new Vector3f(normal);
    }

    /** Enables or disables world click detection tiles on this canvas. */
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
     * @return horizontal anchor coordinate in pixels
     */
    public double anchorX(HAlign align) {
        return switch (align) {
            case LEFT -> paddingPx;
            case CENTER -> resolution.pixelWidth() / 2.0;
            case RIGHT -> resolution.pixelWidth() - paddingPx;
        };
    }

    /**
     * @return vertical anchor coordinate in pixels
     */
    public double anchorY(VAlign align) {
        return switch (align) {
            case TOP -> paddingPx;
            case MIDDLE -> resolution.pixelHeight() / 2.0;
            case BOTTOM -> resolution.pixelHeight() - paddingPx;
        };
    }

    /**
     * @return anchor point coordinates as an array of X and Y
     */
    public double[] anchorPoint(HAlign x, VAlign y) {
        return new double[] { anchorX(x), anchorY(y) };
    }

    /** Calculates intersection point between a ray and the canvas plane in pixels. */
    public double[] rayHit(Vector origin, Vector dir) {
        Vector3f o = new Vector3f((float) origin.getX(), (float) origin.getY(), (float) origin.getZ());
        Vector3f d = new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ());
        float denom = d.dot(normal);

        if (Math.abs(denom) < 1.0e-6f) {
            return null;
        }

        // Compute canvas surface location including layer depth.
        float outward = depthOffset + Layers.text();
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
     * @return intersection pixel coordinates if inside bounds, or null
     */
    public double[] rayHitInside(Vector origin, Vector dir) {
        double[] hit = rayHit(origin, dir);
        if (hit == null) {
            return null;
        }

        if (hit[0] < 0 || hit[0] > resolution.pixelWidth() || hit[1] < 0 || hit[1] > resolution.pixelHeight()) {
            return null;
        }

        return hit;
    }
}
