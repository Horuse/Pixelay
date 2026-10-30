package dev.horuse.pixelay.layout;

/**
 * Evaluates cubic bezier curves following the CSS Transitions specification.
 * Calculates curves using Newton-Raphson iteration with binary subdivision fallback.
 */
public final class CubicBezierEasing implements Easing {

    private static final double EPSILON = 1e-6;
    private static final int MAX_NEWTON_ITERATIONS = 8;

    private final double ax;
    private final double bx;
    private final double cx;

    private final double ay;
    private final double by;
    private final double cy;

    public CubicBezierEasing(double x1, double y1, double x2, double y2) {
        double clX1 = Math.max(0.0, Math.min(1.0, x1));
        double clX2 = Math.max(0.0, Math.min(1.0, x2));

        this.cx = 3.0 * clX1;
        this.bx = 3.0 * (clX2 - clX1) - cx;
        this.ax = 1.0 - cx - bx;

        this.cy = 3.0 * y1;
        this.by = 3.0 * (y2 - y1) - cy;
        this.ay = 1.0 - cy - by;
    }

    @Override
    public double ease(double t) {
        if (t <= 0.0) {
            return 0.0;
        }
        if (t >= 1.0) {
            return 1.0;
        }
        return sampleCurveY(solveCurveX(t));
    }

    private double sampleCurveX(double u) {
        return ((ax * u + bx) * u + cx) * u;
    }

    private double sampleCurveY(double u) {
        return ((ay * u + by) * u + cy) * u;
    }

    private double sampleCurveDerivativeX(double u) {
        return (3.0 * ax * u + 2.0 * bx) * u + cx;
    }

    private double solveCurveX(double x) {
        // Apply Newton-Raphson iteration.
        double u = x;
        for (int i = 0; i < MAX_NEWTON_ITERATIONS; i++) {
            double currentX = sampleCurveX(u) - x;
            if (Math.abs(currentX) < EPSILON) {
                return u;
            }
            double dX = sampleCurveDerivativeX(u);
            if (Math.abs(dX) < 1e-6) {
                break;
            }
            u -= currentX / dX;
        }

        // Use binary subdivision fallback.
        double u0 = 0.0;
        double u1 = 1.0;
        u = x;

        while (u0 < u1) {
            double currentX = sampleCurveX(u);
            if (Math.abs(currentX - x) < EPSILON) {
                return u;
            }
            if (x > currentX) {
                u0 = u;
            } else {
                u1 = u;
            }
            u = (u1 - u0) * 0.5 + u0;
        }

        return u;
    }
}
