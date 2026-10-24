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
 * Text measurement in vanilla font-pixels: character widths are taken from the generated table
 * {@link FontWidths} (vanilla + resource-pack glyphs), bold adds +1px per character, like the
 * client. MiniMessage markup is stripped, but styles (bold) are accounted for. Manual overrides for
 * individual glyphs (e.g. resource-pack icons) are loaded from the plugin's font-widths.yml.
 */
public final class FontMetrics {

    /** Vanilla font line height in font-pixels. */
    public static final float LINE_HEIGHT_PX = 9f;

    /** Distance between consecutive lines of a TextDisplay in font-pixels (glyph height + 1 px spacing). */
    public static final float LINE_PITCH_PX = 10f;

    private static final Map<Integer, Integer> OVERRIDES = new ConcurrentHashMap<>();
    /**
     * Glyphs drawn outside the normal line box (rows 0..9 from the line top), as {@code {top, bottom}}
     * in font px from the line top: a bitmap glyph spans {@code 7 - ascent} to {@code 7 - ascent + height}.
     */
    private static final Map<Integer, int[]> VERTICAL = new ConcurrentHashMap<>();

    private FontMetrics() {}

    /**
     * @return measurement result: width of the widest line (font px) and number of lines (minimum
     *     1)
     */
    public static Metrics measure(String miniMessageText) {
        return measure(miniMessageText, Integer.MAX_VALUE);
    }

    /** Measures text wrapped at {@code maxLineWidth} font px the default way ({@link TextWrap#NORMAL}). */
    public static Metrics measure(String miniMessageText, int maxLineWidth) {
        return measure(miniMessageText, maxLineWidth, TextWrap.NORMAL);
    }

    /**
     * Measures text wrapped at {@code maxLineWidth} font px with {@code wrap}: exactly the lines
     * {@link #wrap} renders (Pixelay breaks the lines itself, the client never wraps).
     */
    public static Metrics measure(String miniMessageText, int maxLineWidth, TextWrap wrap) {
        if (miniMessageText == null || miniMessageText.isEmpty()) {
            return new Metrics(0, 1, 0, LINE_HEIGHT_PX);
        }
        Component root = MiniMessage.miniMessage().deserialize(miniMessageText);
        return TextLayout.metrics(TextLayout.lay(TextLayout.glyphs(root), maxLineWidth, wrap));
    }

    /** A text laid out once: its measurements and the component with the line breaks written in. */
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

        /** The text with Pixelay's line breaks, to send to the client. */
        public Component component() {
            return component;
        }

        /**
         * Where each glyph is drawn when the lines are lined up by {@code align}, in font px from the block's
         * left and its first line's top; {@code null} for glyphs that draw nothing. Index-aligned with the codes
         * of {@link #encoded}.
         */
        public List<GlyphBox> glyphBoxes(LineAlign align) {
            return TextLayout.boxes(lines, align);
        }

        /**
         * {@link #component()} with a 9-bit code hidden in the colour of every glyph (its lowest bits), for the
         * Pixelay core shaders. {@code codes} is index-aligned with {@link #glyphBoxes}.
         */
        public Component encoded(int[] codes) {
            return TextLayout.encode(source, lines, codes);
        }
    }

    /** Measures and wraps a parsed text in one pass (see {@link #measure} and {@link #wrap}). */
    public static Laid lay(Component text, int maxLineWidth, TextWrap wrap) {
        return new Laid(text, TextLayout.lay(TextLayout.glyphs(text), maxLineWidth, wrap));
    }

    /** The component with its line breaks written in, wrapped at {@code maxLineWidth} font px. */
    public static Component wrap(Component text, int maxLineWidth, TextWrap wrap) {
        TextLayout.Lines laid = TextLayout.lay(TextLayout.glyphs(text), maxLineWidth, wrap);
        return TextLayout.apply(text, laid);
    }

    /** The narrowest width in font px the text can wrap to without overflowing (CSS min-content). */
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
     * @return text width in font-pixels (widest line)
     */
    public static int widthFontPx(String miniMessageText) {
        return widthFontPx(miniMessageText, Integer.MAX_VALUE);
    }

    /**
     * @return text width in font-pixels (widest line) after wrapping at {@code maxLineWidth}
     */
    public static int widthFontPx(String miniMessageText, int maxLineWidth) {
        return (int) Math.round(measure(miniMessageText, maxLineWidth).maxWidthPx());
    }

    /**
     * @return text width in canvas px given font size {@code fontHeightPx}
     */
    public static double widthPx(String miniMessageText, double fontHeightPx) {
        return widthPx(miniMessageText, fontHeightPx, Integer.MAX_VALUE);
    }

    /**
     * @return text width in canvas px given font size, after wrapping at {@code maxLineWidth} font px
     */
    public static double widthPx(String miniMessageText, double fontHeightPx, int maxLineWidth) {
        return (widthFontPx(miniMessageText, maxLineWidth) * fontHeightPx) / LINE_HEIGHT_PX;
    }

    /**
     * @return number of text lines (minimum 1)
     */
    public static int lineCount(String miniMessageText) {
        return measure(miniMessageText).lines();
    }

    /**
     * @return number of text lines (minimum 1) after wrapping at {@code maxLineWidth} font px
     */
    public static int lineCount(String miniMessageText, int maxLineWidth) {
        return measure(miniMessageText, maxLineWidth).lines();
    }

    /** Manual glyph width override in font px (takes priority over the table). */
    public static void setOverride(int codePoint, int advancePx) {
        OVERRIDES.put(codePoint, advancePx);
    }

    /**
     * Loads glyph widths on top of the built-in vanilla table: first the ones computed from the packs in
     * {@code resourcepacks/} (written to {@code font-widths.generated.yml}), then the manual entries of
     * {@code font-widths.yml}, which always win.
     */
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

    /**
     * A width entry: either the advance alone ({@code "\\uE000": 12}) or {@code {advance, ascent, height}}
     * for a glyph drawn outside the normal line (ascent and height as in the font provider).
     */
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

    /** Records where a glyph is drawn, if outside the normal line box. */
    static void setVertical(int codePoint, int ascent, int height) {
        int top = 7 - ascent;
        int bottom = top + height;
        if (top < 0 || bottom > LINE_HEIGHT_PX) {
            VERTICAL.put(codePoint, new int[] { top, bottom });
        } else {
            VERTICAL.remove(codePoint);
        }
    }

    /** Rows a glyph is drawn on, {@code {top, bottom}} in font px from the line top. */
    static int[] verticalOf(int codePoint) {
        int[] rows = VERTICAL.get(codePoint);
        return rows != null ? rows : new int[] { 0, (int) LINE_HEIGHT_PX };
    }

    /** Rows of a vanilla glyph's quad: ascent 7, height 8. */
    private static final int[] NORMAL_QUAD_ROWS = { 0, 8 };

    /**
     * Rows the client's quad of a glyph covers, {@code {top, bottom}} in font px from the line top: the glyph's
     * own rows when it is drawn outside the line box, otherwise those of a vanilla glyph.
     */
    static int[] quadRowsOf(int codePoint) {
        int[] rows = VERTICAL.get(codePoint);
        return rows != null ? rows : NORMAL_QUAD_ROWS;
    }

    /** A glyph's advance in font px (+1 when bold), from the overrides or the vanilla table. */
    static int advance(int codePoint, boolean bold) {
        Integer overridden = OVERRIDES.get(codePoint);
        int width = overridden != null ? overridden : FontWidths.advance(codePoint);
        return bold && width > 0 ? width + 1 : width;
    }

    /**
     * @param maxWidthPx width of the widest line (advance)
     * @param lines number of lines
     * @param topPx top of the drawn block in font px from the first line's top: 0 for normal text,
     *     negative when a glyph rises above the line, positive when the first line holds only lower icons
     * @param bottomPx bottom of the drawn block in font px from the first line's top: {@code 9 + 10 *
     *     (lines - 1)} for normal text, more or less when the last line's glyphs hang lower or end higher
     */
    public record Metrics(double maxWidthPx, int lines, double topPx, double bottomPx) {}
}
