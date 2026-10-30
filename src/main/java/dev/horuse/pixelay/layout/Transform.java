package dev.horuse.pixelay.layout;

/**
 * Represents CSS transform values for a box or style.
 * Supports translation, scaling, 2D rotation, and 3D rotation.
 * Supports linear interpolation for CSS transition animations.
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
    /** Default identity transform without translation, scaling, or rotation. */
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

    /** Sets percentage translation relative to box dimensions. */
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

    /** Sets translation in canvas pixels. */
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

    /** Sets uniform scale factor on both axes. */
    public Transform scale(double factor) {
        return scale(factor, factor);
    }

    /** Sets scale factors for X and Y axes. */
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

    /** Sets clockwise 2D rotation in degrees. */
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

    /** Sets 3D pitch rotation in degrees. */
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

    /** Sets 3D yaw rotation in degrees. */
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

    /** Sets transform origin ratios from 0.0 to 1.0. */
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

    /** Linearly interpolates between transforms using normalized time t. */
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
