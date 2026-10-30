package dev.horuse.pixelay.layout;

/**
 * Maps transition progress between 0.0 and 1.0 to an eased output value.
 */
@FunctionalInterface
public interface Easing {
    /** Returns the eased progress for normalized time {@code t} in {@code [0.0, 1.0]}. */
    double ease(double t);

    /** Linear progression with constant speed. */
    Easing LINEAR = t -> t;

    /** Standard ease progression with slow start and deceleration. */
    Easing EASE = cubicBezier(0.25, 0.1, 0.25, 1.0);

    /** Accelerates progression from rest. */
    Easing EASE_IN = cubicBezier(0.42, 0.0, 1.0, 1.0);

    /** Decelerates progression to a stop. */
    Easing EASE_OUT = cubicBezier(0.0, 0.0, 0.58, 1.0);

    /** Slow start and slow finish progression. */
    Easing EASE_IN_OUT = cubicBezier(0.42, 0.0, 0.58, 1.0);

    /** Smoothstep cubic curve progression. */
    Easing SMOOTHSTEP = t -> t * t * (3.0 - 2.0 * t);

    /** Anticipation curve with backward motion before acceleration. */
    Easing EASE_IN_BACK = cubicBezier(0.6, -0.28, 0.735, 0.045);

    /** Overshoot curve extending past target before settling. */
    Easing EASE_OUT_BACK = cubicBezier(0.175, 0.885, 0.32, 1.275);

    /** Bouncing curve settling at destination. */
    Easing BOUNCE_OUT = t -> {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (t < 1 / d1) {
            return n1 * t * t;
        } else if (t < 2 / d1) {
            t -= 1.5 / d1;
            return n1 * t * t + 0.75;
        } else if (t < 2.5 / d1) {
            t -= 2.25 / d1;
            return n1 * t * t + 0.9375;
        } else {
            t -= 2.625 / d1;
            return n1 * t * t + 0.984375;
        }
    };

    /**
     * Creates a cubic bezier timing function matching CSS parameters.
     *
     * @param x1 X coordinate of first control point from 0.0 to 1.0
     * @param y1 Y coordinate of first control point
     * @param x2 X coordinate of second control point from 0.0 to 1.0
     * @param y2 Y coordinate of second control point
     */
    static Easing cubicBezier(double x1, double y1, double x2, double y2) {
        return new CubicBezierEasing(x1, y1, x2, y2);
    }

    /** Creates a stepped progression with the specified interval count. */
    static Easing steps(int intervals) {
        int n = Math.max(1, intervals);
        return t -> Math.min(1.0, Math.floor(t * n) / n);
    }
}
