package dev.horuse.pixelay.utils;

/**
 * Utility methods for ARGB color operations and interpolation.
 */
public final class ColorUtils {

    private ColorUtils() {}

    /**
     * Linearly interpolates between two ARGB colors.
     * Follows CSS Color 4 interpolation rules: if one color has alpha 0 (transparent),
     * its RGB channels take the non-transparent color's channels to prevent dirty grayish fades.
     */
    public static int lerp(int c1, int c2, double t) {
        if (t <= 0.0) {
            return c1;
        }
        if (t >= 1.0) {
            return c2;
        }

        int a1 = (c1 >>> 24) & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF;

        int r1 = (c1 >>> 16) & 0xFF;
        int g1 = (c1 >>> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int r2 = (c2 >>> 16) & 0xFF;
        int g2 = (c2 >>> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        if (a1 == 0) {
            r1 = r2;
            g1 = g2;
            b1 = b2;
        } else if (a2 == 0) {
            r2 = r1;
            g2 = g1;
            b2 = b1;
        }

        int a = Math.clamp(Math.round((float) (a1 + (a2 - a1) * t)), 0, 255);
        int r = Math.clamp(Math.round((float) (r1 + (r2 - r1) * t)), 0, 255);
        int g = Math.clamp(Math.round((float) (g1 + (g2 - g1) * t)), 0, 255);
        int b = Math.clamp(Math.round((float) (b1 + (b2 - b1) * t)), 0, 255);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
