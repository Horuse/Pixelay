package dev.horuse.pixelay.layout;

/**
 * Immutable 2D/3D CSS {@code transform} value for a {@link Box} or {@link Style}, supporting:
 * <ul>
 *   <li>Percentage translation ({@code translateX}, {@code translateY} relative to box width/height, e.g. {@code -0.5} for {@code -50%})</li>
 *   <li>Pixel translation ({@code translatePxX}, {@code translatePxY} in canvas pixels)</li>
 *   <li>Scale ({@code scaleX}, {@code scaleY} multipliers around {@code transformOriginX}, {@code transformOriginY})</li>
 *   <li>Rotation ({@code rotateDeg} in degrees around the canvas Z normal, plus 3D {@code rotateXDeg} / {@code rotateYDeg})</li>
 * </ul>
 * Supports smooth linear interpolation ({@link #lerp(Transform, double)}) for CSS {@code transition} animations.
 */
public record Transform(
    double translateX,
    double translateY,
    double translatePxX,
    double translatePxY,
    double scaleX,
    double scaleY,
    double rotateDeg,
    double rotateXDeg,
    double rotateYDeg,
    double originX,
    double originY
) {
    /** Identity transform ({@code translate(0, 0) scale(1, 1) rotate(0deg)}, origin at center {@code 0.5, 0.5}). */
    public static final Transform NONE = new Transform(0, 0, 0, 0, 1, 1, 0, 0, 0, 0.5, 0.5);

    public static Transform of() {
        return NONE;
    }

    public boolean isIdentity() {
        return (
            Math.abs(translateX) < 1e-6 &&
            Math.abs(translateY) < 1e-6 &&
            Math.abs(translatePxX) < 1e-4 &&
            Math.abs(translatePxY) < 1e-4 &&
            Math.abs(scaleX - 1.0) < 1e-4 &&
            Math.abs(scaleY - 1.0) < 1e-4 &&
            Math.abs(rotateDeg) < 1e-4 &&
            Math.abs(rotateXDeg) < 1e-4 &&
            Math.abs(rotateYDeg) < 1e-4
        );
    }

    public boolean hasRotation() {
        return Math.abs(rotateDeg) >= 1e-4 || Math.abs(rotateXDeg) >= 1e-4 || Math.abs(rotateYDeg) >= 1e-4;
    }

    /** CSS {@code transform: translate(percentX, percentY)} (where {@code 1.0 = 100%} of box width/height). */
    public Transform translate(double percentX, double percentY) {
        return new Transform(
            percentX,
            percentY,
            translatePxX,
            translatePxY,
            scaleX,
            scaleY,
            rotateDeg,
            rotateXDeg,
            rotateYDeg,
            originX,
            originY
        );
    }

    /** CSS {@code transform: translate(dxPx, dyPx)} in canvas pixels. */
    public Transform translatePx(double dxPx, double dyPx) {
        return new Transform(
            translateX,
            translateY,
            dxPx,
            dyPx,
            scaleX,
            scaleY,
            rotateDeg,
            rotateXDeg,
            rotateYDeg,
            originX,
            originY
        );
    }

    public Transform translateX(double dxPx) {
        return translatePx(dxPx, translatePxY);
    }

    public Transform translateY(double dyPx) {
        return translatePx(translatePxX, dyPx);
    }

    /** CSS {@code transform: scale(factor)} (uniform X and Y scale). */
    public Transform scale(double factor) {
        return scale(factor, factor);
    }

    /** CSS {@code transform: scale(sx, sy)}. */
    public Transform scale(double sx, double sy) {
        return new Transform(
            translateX,
            translateY,
            translatePxX,
            translatePxY,
            sx,
            sy,
            rotateDeg,
            rotateXDeg,
            rotateYDeg,
            originX,
            originY
        );
    }

    public Transform scaleX(double sx) {
        return scale(sx, scaleY);
    }

    public Transform scaleY(double sy) {
        return scale(scaleX, sy);
    }

    /** CSS {@code transform: rotate(deg)} (clockwise in degrees on the 2D canvas plane). */
    public Transform rotate(double degrees) {
        return new Transform(
            translateX,
            translateY,
            translatePxX,
            translatePxY,
            scaleX,
            scaleY,
            degrees,
            rotateXDeg,
            rotateYDeg,
            originX,
            originY
        );
    }

    /** CSS {@code transform: rotateX(deg)} (3D pitch tilt in degrees). */
    public Transform rotateX(double degrees) {
        return new Transform(
            translateX,
            translateY,
            translatePxX,
            translatePxY,
            scaleX,
            scaleY,
            rotateDeg,
            degrees,
            rotateYDeg,
            originX,
            originY
        );
    }

    /** CSS {@code transform: rotateY(deg)} (3D yaw turn in degrees). */
    public Transform rotateY(double degrees) {
        return new Transform(
            translateX,
            translateY,
            translatePxX,
            translatePxY,
            scaleX,
            scaleY,
            rotateDeg,
            rotateXDeg,
            degrees,
            originX,
            originY
        );
    }

    /** CSS {@code transform-origin: ox oy} ({@code 0.0..1.0}, default {@code 0.5, 0.5} center). */
    public Transform origin(double ox, double oy) {
        return new Transform(
            translateX,
            translateY,
            translatePxX,
            translatePxY,
            scaleX,
            scaleY,
            rotateDeg,
            rotateXDeg,
            rotateYDeg,
            ox,
            oy
        );
    }

    /** Linearly interpolates between {@code this} and {@code target} by {@code t} ({@code 0.0..1.0}). */
    public Transform lerp(Transform target, double t) {
        if (target == null) {
            target = NONE;
        }
        return new Transform(
            lerpVal(this.translateX, target.translateX, t),
            lerpVal(this.translateY, target.translateY, t),
            lerpVal(this.translatePxX, target.translatePxX, t),
            lerpVal(this.translatePxY, target.translatePxY, t),
            lerpVal(this.scaleX, target.scaleX, t),
            lerpVal(this.scaleY, target.scaleY, t),
            lerpVal(this.rotateDeg, target.rotateDeg, t),
            lerpVal(this.rotateXDeg, target.rotateXDeg, t),
            lerpVal(this.rotateYDeg, target.rotateYDeg, t),
            lerpVal(this.originX, target.originX, t),
            lerpVal(this.originY, target.originY, t)
        );
    }

    public boolean approximatelyEquals(Transform other) {
        if (other == null) {
            return false;
        }
        return (
            Math.abs(translateX - other.translateX) < 1e-5 &&
            Math.abs(translateY - other.translateY) < 1e-5 &&
            Math.abs(translatePxX - other.translatePxX) < 1e-3 &&
            Math.abs(translatePxY - other.translatePxY) < 1e-3 &&
            Math.abs(scaleX - other.scaleX) < 1e-4 &&
            Math.abs(scaleY - other.scaleY) < 1e-4 &&
            Math.abs(rotateDeg - other.rotateDeg) < 1e-3 &&
            Math.abs(rotateXDeg - other.rotateXDeg) < 1e-3 &&
            Math.abs(rotateYDeg - other.rotateYDeg) < 1e-3 &&
            Math.abs(originX - other.originX) < 1e-4 &&
            Math.abs(originY - other.originY) < 1e-4
        );
    }

    private static double lerpVal(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
