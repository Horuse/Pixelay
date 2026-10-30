package dev.horuse.pixelay.utils;

import java.util.Locale;

/**
 * Utility functions for ARGB color operations, CSS color parsing, and interpolation.
 */
public final class ColorUtils {

    private ColorUtils() {}

    /**
     * Parses a CSS color string into a 32-bit integer in {@code 0xAARRGGBB} format.
     * Supports:
     * <ul>
     *   <li>{@code "transparent"} or {@code "none"} -&gt; {@code 0}</li>
     *   <li>{@code "#RGB"}, {@code "#RGBA"}, {@code "#RRGGBB"}, {@code "#RRGGBBAA"}</li>
     *   <li>{@code "0xAARRGGBB"} or {@code "0xRRGGBB"}</li>
     *   <li>{@code "rgb(r, g, b)"} and {@code "rgba(r, g, b, a)"}</li>
     * </ul>
     */
    public static int parse(String cssColor) {
        if (cssColor == null || cssColor.isBlank()) {
            return 0;
        }
        String s = cssColor.trim().toLowerCase(Locale.ROOT);
        if (s.equals("transparent") || s.equals("none")) {
            return 0;
        }
        if (s.startsWith("#")) {
            String hex = s.substring(1);
            return switch (hex.length()) {
                case 3 -> {
                    int r = Character.digit(hex.charAt(0), 16) * 17;
                    int g = Character.digit(hex.charAt(1), 16) * 17;
                    int b = Character.digit(hex.charAt(2), 16) * 17;
                    yield (0xFF << 24) | (r << 16) | (g << 8) | b;
                }
                case 4 -> {
                    int r = Character.digit(hex.charAt(0), 16) * 17;
                    int g = Character.digit(hex.charAt(1), 16) * 17;
                    int b = Character.digit(hex.charAt(2), 16) * 17;
                    int a = Character.digit(hex.charAt(3), 16) * 17;
                    yield (a << 24) | (r << 16) | (g << 8) | b;
                }
                case 6 -> 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
                case 8 -> {
                    int rrggbbaa = Integer.parseUnsignedInt(hex, 16);
                    int rgb = rrggbbaa >>> 8;
                    int a = rrggbbaa & 0xFF;
                    yield (a << 24) | rgb;
                }
                default -> 0;
            };
        }
        if (s.startsWith("0x")) {
            String hex = s.substring(2);
            int val = Integer.parseUnsignedInt(hex, 16);
            return hex.length() <= 6 ? 0xFF000000 | val : val;
        }
        if (s.startsWith("rgba(") && s.endsWith(")")) {
            String[] parts = s.substring(5, s.length() - 1).split(",");
            if (parts.length == 4) {
                int r = Math.clamp(Integer.parseInt(parts[0].trim()), 0, 255);
                int g = Math.clamp(Integer.parseInt(parts[1].trim()), 0, 255);
                int b = Math.clamp(Integer.parseInt(parts[2].trim()), 0, 255);
                double rawA = Double.parseDouble(parts[3].trim());
                int a =
                    rawA <= 1.0
                        ? Math.clamp((int) Math.round(rawA * 255.0), 0, 255)
                        : Math.clamp((int) Math.round(rawA), 0, 255);
                return (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        if (s.startsWith("rgb(") && s.endsWith(")")) {
            String[] parts = s.substring(4, s.length() - 1).split(",");
            if (parts.length == 3) {
                int r = Math.clamp(Integer.parseInt(parts[0].trim()), 0, 255);
                int g = Math.clamp(Integer.parseInt(parts[1].trim()), 0, 255);
                int b = Math.clamp(Integer.parseInt(parts[2].trim()), 0, 255);
                return (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return 0;
    }

    /** Multiplies the alpha channel of {@code argb} by {@code opacity} (0.0 to 1.0). */
    public static int withOpacity(int argb, double opacity) {
        if (argb == 0 || opacity >= 0.999) {
            return argb;
        }
        if (opacity <= 0.001) {
            return 0;
        }
        int a = (argb >>> 24) & 0xFF;
        int scaledA = Math.clamp((int) Math.round(a * opacity), 0, 255);
        return (scaledA << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Interpolates linearly between two ARGB colors following CSS Color 4 standards.
     * Replaces transparent RGB channels with target color channels to prevent gray transitions.
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
