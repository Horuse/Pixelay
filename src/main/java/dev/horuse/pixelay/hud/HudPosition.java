package dev.horuse.pixelay.hud;

import dev.horuse.pixelay.content.FontMetrics;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Positions components on screen with exact horizontal and vertical pixel coordinates.
 * Horizontal offset uses space glyph advances.
 * Vertical offset uses font provider ascents.
 */
public final class HudPosition {

    private static final String NAMESPACE = "pixelay";

    private HudPosition() {}

    /**
     * Returns the font key for the specified anchor and vertical pixel offset.
     *
     * @param anchor the screen anchor
     * @param y vertical offset in pixels
     * @return the Adventure font key
     */
    public static Key font(HudAnchor anchor, int y) {
        int anchorId = anchor != null ? anchor.id() : 0;
        String yStr = y < 0 ? "m" + Math.abs(y) : String.valueOf(y);
        return Key.key(NAMESPACE, "hud_" + anchorId + "_" + yStr);
    }

    /**
     * Returns the font key for the specified vertical pixel offset at default anchor.
     *
     * @param y vertical offset in pixels
     * @return the Adventure font key
     */
    public static Key font(int y) {
        return Key.key(NAMESPACE, "y_" + y);
    }

    /**
     * Positions a component at an exact (x, y) offset on screen with anchor.
     *
     * @param component text component to position
     * @param anchor target screen anchor
     * @param x horizontal pixel offset from anchor
     * @param y vertical pixel offset
     * @param width visual width of component in pixels to return cursor
     * @return positioned composite component
     */
    public static Component at(Component component, HudAnchor anchor, int x, int y, int width) {
        if (component == null) {
            return Component.empty();
        }
        return Component.text()
            .append(HudSpace.offset(x))
            .append(component.font(font(anchor, y)))
            .append(HudSpace.offset(-x - width))
            .build();
    }

    /**
     * Positions a component at an exact (x, y) offset on screen.
     *
     * @param component text component to position
     * @param x horizontal pixel offset from anchor
     * @param y vertical pixel offset
     * @param width visual width of component in pixels to return cursor
     * @return positioned composite component
     */
    public static Component at(Component component, int x, int y, int width) {
        return at(component, HudAnchor.TOP_LEFT, x, y, width);
    }

    /**
     * Positions a component at an exact (x, y) offset on screen.
     * The method calculates component visual width automatically.
     *
     * @param component text component to position
     * @param x horizontal pixel offset from anchor
     * @param y vertical pixel offset
     * @return positioned composite component
     */
    public static Component at(Component component, int x, int y) {
        int width = visualWidth(component);
        return at(component, x, y, width);
    }

    /**
     * Calculates the total visual width of a component in font pixels.
     *
     * @param component the text component to measure
     * @return total width in font pixels
     */
    public static int visualWidth(Component component) {
        if (component == null) {
            return 0;
        }
        boolean bold = component.hasDecoration(TextDecoration.BOLD);
        int width = 0;
        if (component instanceof TextComponent tc) {
            String content = tc.content();
            for (int i = 0; i < content.length(); i++) {
                int cp = content.codePointAt(i);
                if (Character.isSupplementaryCodePoint(cp)) {
                    i++;
                }
                width += FontMetrics.advance(cp, bold);
            }
        }
        for (Component child : component.children()) {
            width += visualWidth(child);
        }
        return width;
    }
}
