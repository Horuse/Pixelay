package dev.horuse.pixelay.content;

import dev.horuse.pixelay.config.YamlFiles;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
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

    /**
     * Measures text the way a TextDisplay with the given {@code line_width} renders it: lines wider
     * than {@code maxLineWidth} font px are word-wrapped like the client does, so the line count and
     * the widest line match what the player sees.
     */
    public static Metrics measure(String miniMessageText, int maxLineWidth) {
        if (miniMessageText == null || miniMessageText.isEmpty()) {
            return new Metrics(0, 1, 0, LINE_HEIGHT_PX);
        }

        Component root = MiniMessage.miniMessage().deserialize(miniMessageText);
        Walker walker = new Walker(maxLineWidth);
        walk(root, false, walker);
        double width = walker.finish();
        return new Metrics(width, walker.lines, walker.top, walker.bottom);
    }

    /**
     * @return text width in font-pixels (widest line)
     */
    public static int widthVanilla(String miniMessageText) {
        return widthVanilla(miniMessageText, Integer.MAX_VALUE);
    }

    /**
     * @return text width in font-pixels (widest line) after wrapping at {@code maxLineWidth}
     */
    public static int widthVanilla(String miniMessageText, int maxLineWidth) {
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
        return (widthVanilla(miniMessageText, maxLineWidth) * fontHeightPx) / LINE_HEIGHT_PX;
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

    /**
     * @return width in font px of the widest word (text between spaces/line breaks), styles included:
     *     the narrowest line width at which no word has to be broken
     */
    public static int longestWordWidth(String miniMessageText) {
        if (miniMessageText == null || miniMessageText.isEmpty()) {
            return 0;
        }
        double[] state = new double[2]; // {current word, widest word}
        walkWords(MiniMessage.miniMessage().deserialize(miniMessageText), false, state);
        return (int) Math.ceil(Math.max(state[0], state[1]));
    }

    private static void walkWords(Component component, boolean parentBold, double[] state) {
        TextDecoration.State boldState = component.style().decoration(TextDecoration.BOLD);
        boolean bold =
            boldState == TextDecoration.State.TRUE || (boldState != TextDecoration.State.FALSE && parentBold);
        if (component instanceof TextComponent text) {
            String content = text.content();
            for (int i = 0; i < content.length(); ) {
                int codePoint = content.codePointAt(i);
                if (codePoint == ' ' || codePoint == '\n') {
                    state[1] = Math.max(state[1], state[0]);
                    state[0] = 0;
                } else {
                    state[0] += advance(codePoint, bold);
                }
                i += Character.charCount(codePoint);
            }
        }
        for (Component child : component.children()) {
            walkWords(child, bold, state);
        }
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

    private static void walk(Component component, boolean parentBold, Walker walker) {
        TextDecoration.State boldState = component.style().decoration(TextDecoration.BOLD);
        boolean bold =
            boldState == TextDecoration.State.TRUE || (boldState != TextDecoration.State.FALSE && parentBold);
        if (component instanceof TextComponent text) {
            String content = text.content();

            for (int i = 0; i < content.length(); ) {
                int codePoint = content.codePointAt(i);

                if (codePoint == '\n') {
                    walker.newline();
                } else {
                    int[] vertical = VERTICAL.get(codePoint);
                    walker.add(
                        advance(codePoint, bold),
                        codePoint == ' ',
                        vertical != null ? vertical[0] : 0,
                        vertical != null ? vertical[1] : (int) LINE_HEIGHT_PX
                    );
                }

                i += Character.charCount(codePoint);
            }
        }

        for (Component child : component.children()) {
            walk(child, bold, walker);
        }
    }

    private static int advance(int codePoint, boolean bold) {
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

    /**
     * Line width accumulator with client-like word wrapping: when a glyph would overflow the line
     * width, the current word moves to a new line (or the glyph does, if the line is one long word).
     */
    private static final class Walker {

        private final int limit;
        private double current;
        /** Width of the word being typed on the current line (since the last space). */
        private double word;
        private boolean lineHasBreak;
        /** Width of the space before the current word: swallowed at a wrap, not part of either line. */
        private double breakSpace;
        private double max;
        private int lines = 1;
        /**
         * Vertical extent of the current line without / of the current word (font px from the line top),
         * from the glyphs actually on it: a normal glyph spans 0..9, an icon its own rows. Empty = none yet.
         */
        private int lineTop = Integer.MAX_VALUE;
        private int lineBottom = Integer.MIN_VALUE;
        private int wordTop = Integer.MAX_VALUE;
        private int wordBottom = Integer.MIN_VALUE;
        /** Extent of the whole block, from the first line's top. */
        double top = Double.MAX_VALUE;
        double bottom = -Double.MAX_VALUE;

        Walker(int limit) {
            this.limit = limit;
        }

        void add(double width, boolean space, int glyphTop, int glyphBottom) {
            if (current + width > limit && current > 0) {
                wrap(space);
                if (space) {
                    return;
                }
            }

            current += width;
            if (space) {
                word = 0;
                breakSpace = width;
                lineHasBreak = true;
                mergeWord();
            } else {
                word += width;
                wordTop = Math.min(wordTop, glyphTop);
                wordBottom = Math.max(wordBottom, glyphBottom);
            }
        }

        /** Breaks the line before the overflowing glyph; a space at the break is swallowed like in the client. */
        private void wrap(boolean space) {
            if (!space && lineHasBreak && word < current) {
                // The current word moves to the next line, together with its height.
                max = Math.max(max, current - word - breakSpace);
                current = word;
                endLine();
                lineTop = wordTop;
                lineBottom = wordBottom;
            } else {
                max = Math.max(max, current);
                current = 0;
                word = 0;
                mergeWord();
                endLine();
            }
            resetWord();
            lines++;
            lineHasBreak = false;
        }

        void newline() {
            max = Math.max(max, current);
            mergeWord();
            endLine();
            resetWord();
            lines++;
            current = 0;
            word = 0;
            lineHasBreak = false;
        }

        double finish() {
            mergeWord();
            endLine();
            return Math.max(max, current);
        }

        private void mergeWord() {
            lineTop = Math.min(lineTop, wordTop);
            lineBottom = Math.max(lineBottom, wordBottom);
            resetWord();
        }

        private void resetWord() {
            wordTop = Integer.MAX_VALUE;
            wordBottom = Integer.MIN_VALUE;
        }

        /** Adds the current line's extent to the block (a line without glyphs counts as a normal one). */
        private void endLine() {
            double lineStart = (lines - 1) * LINE_PITCH_PX;
            boolean empty = lineTop > lineBottom;
            top = Math.min(top, lineStart + (empty ? 0 : lineTop));
            bottom = Math.max(bottom, lineStart + (empty ? LINE_HEIGHT_PX : lineBottom));
            lineTop = Integer.MAX_VALUE;
            lineBottom = Integer.MIN_VALUE;
        }
    }
}
