package dev.horuse.pixelay.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;

/**
 * Rounded rectangles for Screen HUD backgrounds (CSS {@code background-color},
 * {@code border} and {@code border-radius}).
 *
 * <p>A panel is drawn from glyphs of one height: a left cap, a right cap and strips of
 * 1, 2, 4 ... 256 pixels between them. Each glyph advances by its width plus one pixel, so a
 * one-pixel space follows every glyph. Colors are baked into the textures and the glyphs
 * render white without a text shadow, so the panel shows the exact configured colors.
 *
 * <p>Glyphs live in {@code pixelay:panel_<anchor>_<y>} fonts. A panel style must be registered
 * before the pack is built, otherwise its glyphs are missing until the next pack build.
 */
public final class HudPanel {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String NAMESPACE = "pixelay";
    private static final int CHAR_BASE = 0xE000;
    private static final int PIECES_PER_STYLE = 16;
    private static final int PIECE_LEFT_CAP = 0;
    private static final int PIECE_RIGHT_CAP = 1;
    private static final int PIECE_STRIP = 2;
    /** Strip widths, widest first. */
    private static final int[] STRIPS = { 256, 128, 64, 32, 16, 8, 4, 2, 1 };
    private static final int MAX_STYLES = 256;

    /**
     * Visual properties shared by every panel glyph of one style.
     *
     * @param height panel height in pixels
     * @param radius corner radius in pixels
     * @param fill background color (ARGB)
     * @param borderWidth border width in pixels
     * @param border border color (ARGB)
     */
    public record Style(int height, int radius, int fill, int borderWidth, int border) {
        public Style {
            height = Math.clamp(height, 1, 256);
            radius = Math.clamp(radius, 0, height / 2);
            borderWidth = Math.clamp(borderWidth, 0, height / 2);
        }

        /** Returns the width of each cap: wide enough for the corner radius and the side border. */
        int capWidth() {
            return Math.max(radius, borderWidth);
        }

        boolean visible() {
            return alpha(fill) > 0 || (borderWidth > 0 && alpha(border) > 0);
        }

        String id() {
            return "h" + height + "_r" + radius + "_f" + hex(fill) + "_b" + borderWidth + "_" + hex(border);
        }
    }

    private record Slot(HudAnchor anchor, int y) {}

    private static final Map<Slot, List<Style>> STYLES = new ConcurrentHashMap<>();

    private HudPanel() {}

    /**
     * Registers a panel style at a font coordinate so its glyphs are written into the pack.
     *
     * @param anchor screen anchor
     * @param y font Y of the panel top edge
     * @param style panel style
     * @return index of the style inside the slot font, or -1 when the slot is full
     */
    public static int register(HudAnchor anchor, int y, Style style) {
        List<Style> list = STYLES.computeIfAbsent(new Slot(anchor, y), k -> new ArrayList<>());
        synchronized (list) {
            int index = list.indexOf(style);
            if (index >= 0) {
                return index;
            }
            if (list.size() >= MAX_STYLES) {
                return -1;
            }
            list.add(style);
            return list.size() - 1;
        }
    }

    /**
     * Renders a panel. The result has a net horizontal advance of zero.
     *
     * @param anchor screen anchor
     * @param x horizontal offset of the panel left edge from the anchor
     * @param y font Y of the panel top edge
     * @param width panel width in pixels
     * @param style panel style
     * @return panel component, or empty when the panel is invisible
     */
    public static Component render(HudAnchor anchor, int x, int y, int width, Style style) {
        if (width <= 0 || !style.visible()) {
            return Component.empty();
        }
        int index = register(anchor, y, style);
        if (index < 0) {
            return Component.empty();
        }

        int cap = style.capWidth();
        StringBuilder glyphs = new StringBuilder();
        int remaining = width;
        if (cap > 0 && width >= cap * 2) {
            glyphs.append(glyph(index, PIECE_LEFT_CAP));
            remaining -= cap * 2;
        } else {
            cap = 0;
        }
        for (int i = 0; i < STRIPS.length; i++) {
            while (remaining >= STRIPS[i]) {
                glyphs.append(glyph(index, PIECE_STRIP + i));
                remaining -= STRIPS[i];
            }
        }
        if (cap > 0) {
            glyphs.append(glyph(index, PIECE_RIGHT_CAP));
        }

        // Every glyph advances one pixel past its width; a -1 space after each glyph removes it.
        Key panelFont = font(anchor, y);
        TextComponent.Builder body = Component.text();
        for (int i = 0; i < glyphs.length(); i++) {
            body.append(
                Component.text(String.valueOf(glyphs.charAt(i)), NamedTextColor.WHITE)
                    .font(panelFont)
                    .shadowColor(ShadowColor.none())
            );
            body.append(HudSpace.offset(-1));
        }

        return Component.text()
            .append(HudSpace.offset(x))
            .append(body.build())
            .append(HudSpace.offset(-x - width))
            .build();
    }

    /** Clears all registered panel styles. */
    public static void clear() {
        STYLES.clear();
    }

    /**
     * Writes panel textures and fonts for every registered style.
     *
     * @param outputFiles map that receives the generated asset files
     */
    public static void generate(Map<String, byte[]> outputFiles) {
        Set<Style> textures = new HashSet<>();
        for (Map.Entry<Slot, List<Style>> entry : STYLES.entrySet()) {
            Slot slot = entry.getKey();
            int shaderId = slot.anchor().id() + 1;
            int baseNumber = ((1024 + shaderId) << 13) + 4105;
            // Same glyph top as text glyphs at this Y (see YFontGenerator).
            int ascent = -(baseNumber + slot.y() - 7);

            List<Style> styles;
            synchronized (entry.getValue()) {
                styles = List.copyOf(entry.getValue());
            }
            JsonArray providers = new JsonArray();
            for (int index = 0; index < styles.size(); index++) {
                Style style = styles.get(index);
                textures.add(style);
                if (style.capWidth() > 0) {
                    providers.add(provider(style, "left", ascent, glyph(index, PIECE_LEFT_CAP)));
                    providers.add(provider(style, "right", ascent, glyph(index, PIECE_RIGHT_CAP)));
                }
                for (int i = 0; i < STRIPS.length; i++) {
                    providers.add(provider(style, "w" + STRIPS[i], ascent, glyph(index, PIECE_STRIP + i)));
                }
            }

            JsonObject root = new JsonObject();
            root.add("providers", providers);
            outputFiles.put(
                "assets/" + NAMESPACE + "/font/" + fontName(slot.anchor(), slot.y()) + ".json",
                GSON.toJson(root).getBytes(StandardCharsets.UTF_8)
            );
        }

        for (Style style : textures) {
            if (style.capWidth() > 0) {
                outputFiles.put(texturePath(style, "left"), png(cap(style, true)));
                outputFiles.put(texturePath(style, "right"), png(cap(style, false)));
            }
            for (int w : STRIPS) {
                outputFiles.put(texturePath(style, "w" + w), png(strip(style, w)));
            }
        }
    }

    private static String glyph(int styleIndex, int piece) {
        return String.valueOf((char) (CHAR_BASE + styleIndex * PIECES_PER_STYLE + piece));
    }

    private static Key font(HudAnchor anchor, int y) {
        return Key.key(NAMESPACE, fontName(anchor, y));
    }

    private static String fontName(HudAnchor anchor, int y) {
        return "panel_" + anchor.id() + "_" + (y < 0 ? "m" + Math.abs(y) : String.valueOf(y));
    }

    private static String textureName(Style style, String piece) {
        return "hud/panel/" + style.id() + "_" + piece;
    }

    private static String texturePath(Style style, String piece) {
        return "assets/" + NAMESPACE + "/textures/" + textureName(style, piece) + ".png";
    }

    private static JsonObject provider(Style style, String piece, int ascent, String chars) {
        JsonObject p = new JsonObject();
        p.addProperty("type", "bitmap");
        p.addProperty("file", NAMESPACE + ":" + textureName(style, piece) + ".png");
        p.addProperty("ascent", ascent);
        p.addProperty("height", style.height());
        JsonArray list = new JsonArray();
        list.add(chars);
        p.add("chars", list);
        return p;
    }

    private static BufferedImage strip(Style style, int width) {
        int h = style.height();
        int bw = style.borderWidth();
        BufferedImage img = new BufferedImage(width, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            int argb = y < bw || y >= h - bw ? style.border() : style.fill();
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    private static BufferedImage cap(Style style, boolean left) {
        int c = style.capWidth();
        int h = style.height();
        BufferedImage img = new BufferedImage(c, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < c; x++) {
                // Distance from the outer side edge, so both caps share one shape.
                int fromEdge = left ? x : c - 1 - x;
                img.setRGB(x, y, capPixel(style, fromEdge, y));
            }
        }
        return img;
    }

    private static int capPixel(Style style, int fromEdge, int y) {
        int r = style.radius();
        int h = style.height();
        int bw = style.borderWidth();
        boolean inCornerRows = y < r || y >= h - r;
        if (r > 0 && fromEdge < r && inCornerRows) {
            double dx = r - (fromEdge + 0.5);
            double dy = y < r ? r - (y + 0.5) : y + 0.5 - (h - r);
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance > r) {
                return 0;
            }
            return distance > r - bw ? style.border() : style.fill();
        }
        boolean border = fromEdge < bw || y < bw || y >= h - bw;
        return border ? style.border() : style.fill();
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static String hex(int argb) {
        return String.format("%08x", argb);
    }

    private static byte[] png(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
