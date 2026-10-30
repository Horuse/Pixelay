package dev.horuse.pixelay.layout;

/**
 * Timing function that maps transition progress {@code t} in {@code [0.0, 1.0]} to eased output.
 * Matches standard CSS transition timing functions ({@code ease}, {@code ease-in}, {@code ease-out},
 * {@code ease-in-out}, {@code linear}, and {@code cubic-bezier(x1, y1, x2, y2)}).
 */
@FunctionalInterface
public interface Easing {
    /** Returns the eased progress for normalized time {@code t} in {@code [0.0, 1.0]}. */
    double ease(double t);

    /** Linear transition ({@code t -> t}): constant speed from start to end. */
    Easing LINEAR = t -> t;

    /** CSS {@code ease} ({@code cubic-bezier(0.25, 0.1, 0.25, 1.0)}): slow start, fast middle, gentle deceleration. */
    Easing EASE = cubicBezier(0.25, 0.1, 0.25, 1.0);

    /** CSS {@code ease-in} ({@code cubic-bezier(0.42, 0.0, 1.0, 1.0)}): accelerates from rest. */
    Easing EASE_IN = cubicBezier(0.42, 0.0, 1.0, 1.0);

    /** CSS {@code ease-out} ({@code cubic-bezier(0.0, 0.0, 0.58, 1.0)}): decelerates to a stop. */
    Easing EASE_OUT = cubicBezier(0.0, 0.0, 0.58, 1.0);

    /** CSS {@code ease-in-out} ({@code cubic-bezier(0.42, 0.0, 0.58, 1.0)}): slow start and slow finish. */
    Easing EASE_IN_OUT = cubicBezier(0.42, 0.0, 0.58, 1.0);

    /** Hermite smoothstep curve: {@code 3t^2 - 2t^3}. */
    Easing SMOOTHSTEP = t -> t * t * (3.0 - 2.0 * t);

    /** Anticipation curve with slight backstep before acceleration. */
    Easing EASE_IN_BACK = cubicBezier(0.6, -0.28, 0.735, 0.045);

    /** Overshoot curve with subtle spring past target before settling. */
    Easing EASE_OUT_BACK = cubicBezier(0.175, 0.885, 0.32, 1.275);

    /** Bouncy settling curve. */
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
     * Creates a custom cubic bezier timing function matching CSS {@code cubic-bezier(x1, y1, x2, y2)}.
     *
     * @param x1 x coordinate of first control point, clamped to {@code [0.0, 1.0]}
     * @param y1 y coordinate of first control point
     * @param x2 x coordinate of second control point, clamped to {@code [0.0, 1.0]}
     * @param y2 y coordinate of second control point
     */
    static Easing cubicBezier(double x1, double y1, double x2, double y2) {
        return new CubicBezierEasing(x1, y1, x2, y2);
    }

    /** Stepped progression dividing the duration into {@code intervals} equal jumps. */
    static Easing steps(int intervals) {
        int n = Math.max(1, intervals);
        return t -> Math.min(1.0, Math.floor(t * n) / n);
    }
}
