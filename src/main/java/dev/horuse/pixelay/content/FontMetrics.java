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
            return new Metrics(0, 1);
        }

        Component root = MiniMessage.miniMessage().deserialize(miniMessageText);
        Walker walker = new Walker(maxLineWidth);
        walk(root, false, walker);
        return new Metrics(walker.finish(), walker.lines);
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
     * Loads overrides from the plugin's font-widths.yml: glyphs section, key is a single character,
     * value is width in font px.
     */
    public static void loadOverrides(JavaPlugin plugin) throws ConfigurateException {
        OVERRIDES.clear();
        ConfigurationNode glyphs = YamlFiles.load(plugin, "font-widths.yml").node("glyphs");

        for (Map.Entry<Object, ? extends ConfigurationNode> entry : glyphs.childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());

            if (key.codePointCount(0, key.length()) != 1) {
                plugin.getLogger().warning("font-widths.yml: key '" + key + "' is not a single character, skipped");
                continue;
            }

            OVERRIDES.put(key.codePointAt(0), entry.getValue().getInt());
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
                    walker.add(advance(codePoint, bold), codePoint == ' ');
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
     * @param maxWidthPx width of the widest line, @param lines number of lines
     */
    public record Metrics(double maxWidthPx, int lines) {}

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

        Walker(int limit) {
            this.limit = limit;
        }

        void add(double width, boolean space) {
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
            } else {
                word += width;
            }
        }

        /** Breaks the line before the overflowing glyph; a space at the break is swallowed like in the client. */
        private void wrap(boolean space) {
            lines++;
            if (!space && lineHasBreak && word < current) {
                max = Math.max(max, current - word - breakSpace);
                current = word;
            } else {
                max = Math.max(max, current);
                current = 0;
                word = 0;
            }

            lineHasBreak = false;
        }

        void newline() {
            max = Math.max(max, current);
            lines++;
            current = 0;
            word = 0;
            lineHasBreak = false;
        }

        double finish() {
            return Math.max(max, current);
        }
    }
}
