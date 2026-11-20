package dev.horuse.pixelay.hud;

import dev.horuse.pixelay.content.FontMetrics;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

/**
 * Encodes screen anchor metadata and line indices into component colors for vertex shaders.
 * Cancels horizontal line advances using negative spaces so that total text width is zero.
 */
public final class HudCodec {

    private static final int DEFAULT_COLOR = 0xFFFFFF;
    private static final int HUD_TAG = 256;
    private static final int COMPASS_TAG = 5;

    private HudCodec() {}

    /**
     * Encodes anchor and line index into the color of each glyph in the component.
     * Appends negative space equal to the width of the line so that cursor resets.
     *
     * @param component the text component to encode
     * @param anchor the target screen anchor
     * @param lineIndex line index from 0 to 15
     * @return the encoded composite component
     */
    public static Component encodeLine(Component component, HudAnchor anchor, int lineIndex) {
        if (component == null) {
            return Component.empty();
        }
        int code = HUD_TAG | ((lineIndex & 15) << 4) | (anchor.id() & 15);
        int[] widthTracker = new int[] { 0 };
        Component encoded = encodeComponent(component, code, DEFAULT_COLOR, widthTracker);
        int lineWidth = widthTracker[0];
        if (lineWidth <= 0) {
            return encoded;
        }
        return Component.text().append(encoded).append(HudSpace.offset(-lineWidth)).build();
    }

    /**
     * Encodes compass tape yaw data and compass marker tag into component colors.
     *
     * @param component the tape text component
     * @param targetYaw target player yaw in degrees (0..360)
     * @param prevYaw previous player yaw in degrees (0..360)
     * @return the encoded component
     */
    public static Component encodeCompassTape(Component component, float targetYaw, float prevYaw) {
        if (component == null) {
            return Component.empty();
        }
        int normTarget = Math.round(((((targetYaw % 360.0f) + 360.0f) % 360.0f) / 360.0f) * 255.0f);
        int normPrev = Math.round(((((prevYaw % 360.0f) + 360.0f) % 360.0f) / 360.0f) * 255.0f);
        int r = Math.clamp(normTarget, 0, 255);
        int g = Math.clamp(normPrev, 0, 255);
        int b = 0xF8 | COMPASS_TAG;
        int packedRgb = (r << 16) | (g << 8) | b;
        return applyFixedColor(component, packedRgb);
    }

    private static Component encodeComponent(Component component, int code, int inheritedColor, int[] widthTracker) {
        int color = component.style().color() != null ? component.style().color().value() : inheritedColor;
        boolean bold = component.style().hasDecoration(net.kyori.adventure.text.format.TextDecoration.BOLD);
        List<Component> children = new ArrayList<>();
        Component result = component;

        if (component instanceof TextComponent text) {
            String content = text.content();
            for (int i = 0; i < content.length(); ) {
                int codePoint = content.codePointAt(i);
                widthTracker[0] += FontMetrics.advance(codePoint, bold);
                String piece = new String(Character.toChars(codePoint));
                children.add(Component.text(piece, TextColor.color(withCode(color, code))));
                i += Character.charCount(codePoint);
            }
            result = text.content("");
        }

        for (Component child : component.children()) {
            children.add(encodeComponent(child, code, color, widthTracker));
        }

        return result.children(children);
    }

    private static Component applyFixedColor(Component component, int rgb) {
        List<Component> children = new ArrayList<>();
        Component result = component;
        if (component instanceof TextComponent text) {
            result = text.color(TextColor.color(rgb));
        }
        for (Component child : component.children()) {
            children.add(applyFixedColor(child, rgb));
        }
        return result.children(children);
    }

    private static int withCode(int rgb, int code) {
        int red = ((rgb >> 16) & 0xF8) | (code & 7);
        int green = ((rgb >> 8) & 0xF8) | ((code >> 3) & 7);
        int blue = (rgb & 0xF8) | ((code >> 6) & 7);
        return (red << 16) | (green << 8) | blue;
    }
}
