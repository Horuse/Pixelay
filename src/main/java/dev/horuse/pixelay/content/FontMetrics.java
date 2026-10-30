package dev.horuse.pixelay.content;

import dev.horuse.pixelay.config.YamlFiles;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Measures text in vanilla font pixels.
 * Character widths come from the generated table {@link FontWidths}.
 * Bold style adds one pixel per character.
 * MiniMessage markup is removed, but styles stay active.
 * Manual overrides load from font-widths.yml.
 */
public final class FontMetrics {

    /** Vanilla font line height in font pixels. */
    public static final float LINE_HEIGHT_PX = 9f;

    /** Distance between consecutive lines of a TextDisplay in font pixels. */
    public static final float LINE_PITCH_PX = 10f;

    private static final Map<Integer, Integer> OVERRIDES = new ConcurrentHashMap<>();
    /** Glyphs rendered outside the standard line box. */
    private static final Map<Integer, int[]> VERTICAL = new ConcurrentHashMap<>();

    private FontMetrics() {}

    /**
     * @return measurement result with the widest line width and line count
     */
    public static Metrics measure(String miniMessageText) {
        return measure(miniMessageText, Integer.MAX_VALUE);
    }

    /** Measures text wrapped at {@code maxLineWidth} font pixels with {@link TextWrap#NORMAL}. */
    public static Metrics measure(String miniMessageText, int maxLineWidth) {
        return measure(miniMessageText, maxLineWidth, TextWrap.NORMAL);
    }

    /** Measures text wrapped at {@code maxLineWidth} font pixels with {@code wrap}. */
    public static Metrics measure(String miniMessageText, int maxLineWidth, TextWrap wrap) {
        if (miniMessageText == null || miniMessageText.isEmpty()) {
            return new Metrics(0, 1, 0, LINE_HEIGHT_PX);
        }
        Component root = MiniMessage.miniMessage().deserialize(miniMessageText);
        return TextLayout.metrics(TextLayout.lay(TextLayout.glyphs(root), maxLineWidth, wrap));
    }

    /** Text layout result with measurements and line breaks. */
    public static final class Laid {

        private final Component source;
        private final TextLayout.Lines lines;
        private final Metrics metrics;
        private final Component component;

        private Laid(Component source, TextLayout.Lines lines) {
            this.source = source;
            this.lines = lines;
            this.metrics = TextLayout.metrics(lines);
            this.component = TextLayout.apply(source, lines);
        }

        public Metrics metrics() {
            return metrics;
        }

        /** Gets the component with line breaks to send to the client. */
        public Component component() {
            return component;
        }

        /** Calculates where each glyph renders when aligned with {@code align}. */
        public List<GlyphBox> glyphBoxes(LineAlign align) {
            return TextLayout.boxes(lines, align);
        }

        /** Encodes a 9-bit identifier into the lowest bits of glyph colors for shaders. */
        public Component encoded(int[] codes) {
            return TextLayout.encode(source, lines, codes);
        }
    }

    /** Measures and wraps parsed text in one pass. */
    public static Laid lay(Component text, int maxLineWidth, TextWrap wrap) {
        return new Laid(text, TextLayout.lay(TextLayout.glyphs(text), maxLineWidth, wrap));
    }

    /** Truncates a single-line MiniMessage string with ellipsis when width exceeds the limit. */
    public static String truncateEllipsis(String miniMessageText, int maxLineWidth) {
        if (miniMessageText == null || miniMessageText.isEmpty() || maxLineWidth <= 0) {
            return "";
        }
        Component parsed = MiniMessage.miniMessage().deserialize(miniMessageText);
        Component truncated = TextLayout.truncateEllipsis(parsed, maxLineWidth);
        if (truncated == parsed) {
            return miniMessageText;
        }
        return MiniMessage.miniMessage().serialize(truncated);
    }

    /** The component with line breaks, wrapped at {@code maxLineWidth} font pixels. */
    public static Component wrap(Component text, int maxLineWidth, TextWrap wrap) {
        TextLayout.Lines laid = TextLayout.lay(TextLayout.glyphs(text), maxLineWidth, wrap);
        return TextLayout.apply(text, laid);
    }

    /** Calculates the minimum width in font pixels without text overflow. */
    public static int minContentWidth(String miniMessageText, TextWrap wrap) {
        if (miniMessageText == null || miniMessageText.isEmpty()) {
            return 0;
        }
        return TextLayout.minContentWidth(
            TextLayout.glyphs(MiniMessage.miniMessage().deserialize(miniMessageText)),
            wrap
        );
    }

    /**
     * @return text width in font pixels for the widest line
     */
    public static int widthFontPx(String miniMessageText) {
        return widthFontPx(miniMessageText, Integer.MAX_VALUE);
    }

    /**
     * @return text width in font pixels after wrapping at {@code maxLineWidth}
     */
    public static int widthFontPx(String miniMessageText, int maxLineWidth) {
        return (int) Math.round(measure(miniMessageText, maxLineWidth).maxWidthPx());
    }

    /**
     * @return text width in canvas pixels for font height {@code fontHeightPx}
     */
    public static double widthPx(String miniMessageText, double fontHeightPx) {
        return widthPx(miniMessageText, fontHeightPx, Integer.MAX_VALUE);
    }

    /**
     * @return text width in canvas pixels after wrapping at {@code maxLineWidth}
     */
    public static double widthPx(String miniMessageText, double fontHeightPx, int maxLineWidth) {
        return (widthFontPx(miniMessageText, maxLineWidth) * fontHeightPx) / LINE_HEIGHT_PX;
    }

    /**
     * @return number of text lines, with a minimum of 1
     */
    public static int lineCount(String miniMessageText) {
        return measure(miniMessageText).lines();
    }

    /**
     * @return number of text lines after wrapping at {@code maxLineWidth}
     */
    public static int lineCount(String miniMessageText, int maxLineWidth) {
        return measure(miniMessageText, maxLineWidth).lines();
    }

    /** Sets a manual glyph width override in font pixels. */
    public static void setOverride(int codePoint, int advancePx) {
        OVERRIDES.put(codePoint, advancePx);
    }

    /** Loads glyph widths from resource packs and manual configuration. */
    public static void loadOverrides(JavaPlugin plugin) throws ConfigurateException {
        OVERRIDES.clear();
        VERTICAL.clear();
        PackFontWidths.generate(plugin).forEach((codePoint, glyph) -> {
            OVERRIDES.put(codePoint, glyph.advance());
            if (glyph.height() > 0) {
                setVertical(codePoint, glyph.ascent(), glyph.height());
            }
        });
        ConfigurationNode glyphs = YamlFiles.load(plugin, "font-widths.yml").node("glyphs");

        for (Map.Entry<Object, ? extends ConfigurationNode> entry : glyphs.childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());

            if (key.codePointCount(0, key.length()) != 1) {
                plugin.getLogger().warning("font-widths.yml: key '" + key + "' is not a single character, skipped");
                continue;
            }

            putGlyph(key.codePointAt(0), entry.getValue());
        }
    }

    /** Parses a glyph width entry from configuration. */
    static void putGlyph(int codePoint, ConfigurationNode value) {
        if (!value.isMap()) {
            OVERRIDES.put(codePoint, value.getInt());
            VERTICAL.remove(codePoint);
            return;
        }
        if (!value.node("advance").virtual()) {
            OVERRIDES.put(codePoint, value.node("advance").getInt());
        }
        if (!value.node("height").virtual()) {
            int height = value.node("height").getInt();
            int ascent = value.node("ascent").getInt(7);
            setVertical(codePoint, ascent, height);
        }
    }

    /** Stores the vertical bounds of a glyph when it extends outside the standard line box. */
    static void setVertical(int codePoint, int ascent, int height) {
        int top = 7 - ascent;
        int bottom = top + height;
        if (top < 0 || bottom > LINE_HEIGHT_PX) {
            VERTICAL.put(codePoint, new int[] { top, bottom });
        } else {
            VERTICAL.remove(codePoint);
        }
    }

    /** Gets the vertical bounds of a glyph in font pixels from the line top. */
    static int[] verticalOf(int codePoint) {
        int[] rows = VERTICAL.get(codePoint);
        return rows != null ? rows : new int[] { 0, (int) LINE_HEIGHT_PX };
    }

    /** Vertical bounds of a vanilla glyph quad. */
    private static final int[] NORMAL_QUAD_ROWS = { 0, 8 };

    /** Gets the quad bounds of a glyph in font pixels from the line top. */
    static int[] quadRowsOf(int codePoint) {
        int[] rows = VERTICAL.get(codePoint);
        return rows != null ? rows : NORMAL_QUAD_ROWS;
    }

    /** Gets the glyph advance in font pixels, adding one pixel when bold. */
    static int advance(int codePoint, boolean bold) {
        Integer overridden = OVERRIDES.get(codePoint);
        int width = overridden != null ? overridden : FontWidths.advance(codePoint);
        return bold && width > 0 ? width + 1 : width;
    }

    /**
     * @param maxWidthPx width of the widest line
     * @param lines number of lines
     * @param topPx top offset of the rendered block in font pixels
     * @param bottomPx bottom offset of the rendered block in font pixels
     */
    public record Metrics(double maxWidthPx, int lines, double topPx, double bottomPx) {}
}
