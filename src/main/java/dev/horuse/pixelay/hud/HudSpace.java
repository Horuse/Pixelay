package dev.horuse.pixelay.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
 * Encodes horizontal pixel advances using negative and positive space glyphs.
 * Uses font provider {@code pixelay:space} for pixel-accurate layout positioning.
 */
public final class HudSpace {

    /** Font key for Pixelay space advances. */
    public static final Key FONT = Key.key("pixelay", "space");

    /** Magic base vertical offset for HUD elements. */
    public static final int MAGIC_OFFSET = 5_000_000;

    /** Step per anchor index. */
    public static final int ANCHOR_STEP = 100_000;

    private static final int[] LARGE_VALUES = { 1024, 512, 400, 320, 256, 200, 160, 128, 100, 80 };
    private static final int[] LARGE_INDICES = { 9, 8, 7, 6, 5, 4, 3, 2, 1, 0 };

    private HudSpace() {}

    /**
     * Calculates the negative font ascent for a screen anchor and Y offset.
     *
     * @param anchor the screen anchor
     * @param yOffset the vertical pixel offset from the anchor
     * @return the encoded font ascent
     */
    public static int ascent(HudAnchor anchor, int yOffset) {
        return -(MAGIC_OFFSET + anchor.id() * ANCHOR_STEP + yOffset);
    }

    /**
     * Decomposes a horizontal pixel distance into space characters.
     *
     * @param pixels the pixel offset (positive = right, negative = left)
     * @return a string of space glyphs
     */
    public static String advance(int pixels) {
        if (pixels == 0) {
            return "";
        }
        boolean isNegative = pixels < 0;
        int remaining = Math.abs(pixels);
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < LARGE_VALUES.length; i++) {
            int val = LARGE_VALUES[i];
            int charIndex = LARGE_INDICES[i];
            while (remaining >= val) {
                int base = isNegative ? 0xE250 : 0xE350;
                out.append((char) (base + charIndex));
                remaining -= val;
            }
        }

        while (remaining > 0) {
            int step = Math.min(remaining, 64);
            int base = isNegative ? 0xE200 : 0xE300;
            out.append((char) (base + step));
            remaining -= step;
        }

        return out.toString();
    }

    /**
     * Creates a text component with space glyphs and the Pixelay space font.
     *
     * @param pixels the pixel offset
     * @return the text component
     */
    public static Component offset(int pixels) {
        if (pixels == 0) {
            return Component.empty();
        }
        return Component.text(advance(pixels)).font(FONT);
    }
}
