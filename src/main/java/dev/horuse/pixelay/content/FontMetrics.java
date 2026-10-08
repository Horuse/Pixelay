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

    private static final Map<Integer, Integer> OVERRIDES = new ConcurrentHashMap<>();

    private FontMetrics() {}

    /**
     * @return measurement result: width of the widest line (font px) and number of lines (minimum
     *     1)
     */
    public static Metrics measure(String miniMessageText) {
        if (miniMessageText == null || miniMessageText.isEmpty()) {
            return new Metrics(0, 1);
        }

        Component root = MiniMessage.miniMessage().deserialize(miniMessageText);
        Walker walker = new Walker();
        walk(root, false, walker);
        return new Metrics(walker.max, walker.lines);
    }

    /**
     * @return text width in font-pixels (widest line)
     */
    public static int widthVanilla(String miniMessageText) {
        return (int) Math.round(measure(miniMessageText).maxWidthPx());
    }

    /**
     * @return text width in canvas px given font size {@code fontHeightPx}
     */
    public static double widthPx(String miniMessageText, double fontHeightPx) {
        return (widthVanilla(miniMessageText) * fontHeightPx) / LINE_HEIGHT_PX;
    }

    /**
     * @return number of text lines (minimum 1)
     */
    public static int lineCount(String miniMessageText) {
        return measure(miniMessageText).lines();
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
                    walker.add(advance(codePoint, bold));
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

    /** Line width accumulator: current line, maximum, number of lines. */
    private static final class Walker {

        private double current;
        private double max;
        private int lines = 1;

        void add(double width) {
            current += width;
            if (current > max) {
                max = current;
            }
        }

        void newline() {
            lines++;
            current = 0;
        }
    }
}
