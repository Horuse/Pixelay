package dev.horuse.pixelay.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.horuse.pixelay.config.PixelaySettings;
import dev.horuse.pixelay.content.PackFontScanner;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generates font definitions with modified ascent values for vertical positioning.
 * The system writes {@code assets/pixelay/font/y_<offset>.json} files into the pack.
 */
public final class YFontGenerator {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String TEMPLATE_PATH = "pack/assets/pixelay/font/default_font_template.json";

    private YFontGenerator() {}

    /**
     * Generates font files for all configured Y offsets.
     *
     * @param config the font offset configuration
     * @param scannedGlyphs custom glyphs measured from resource packs
     * @param outputFiles map that receives the generated asset files
     */
    public static void generate(
        PixelaySettings.FontYOffsets config,
        Map<Integer, PackFontScanner.Glyph> scannedGlyphs,
        Map<String, byte[]> outputFiles
    ) {
        generate(config, scannedGlyphs, null, outputFiles);
    }

    /**
     * Generates font files for registered (anchor, y) pairs, or falls back to the configured
     * {@code minY..maxY} range when {@code registeredEntries} is null or empty.
     */
    public static void generate(
        PixelaySettings.FontYOffsets config,
        Map<Integer, PackFontScanner.Glyph> scannedGlyphs,
        Set<YFontRegistry.Entry> registeredEntries,
        Map<String, byte[]> outputFiles
    ) {
        if (!config.enabled()) {
            return;
        }

        JsonObject template = loadTemplate();
        JsonArray vanillaProviders = template.getAsJsonArray("providers");

        JsonObject nonlatinProvider = null;
        JsonObject accentedProvider = null;
        JsonObject asciiProvider = null;

        for (JsonElement el : vanillaProviders) {
            JsonObject p = el.getAsJsonObject();
            String file = p.has("file") ? p.get("file").getAsString() : "";
            if (file.contains("nonlatin_european.png")) {
                nonlatinProvider = p;
            } else if (file.contains("accented.png")) {
                accentedProvider = p;
            } else if (file.contains("ascii.png")) {
                asciiProvider = p;
            }
        }

        List<String> langs = config.languages().stream().map(String::toLowerCase).toList();
        boolean includeEn = langs.contains("en") || langs.contains("ascii") || langs.contains("latin");
        boolean includeUk = langs.contains("uk") || langs.contains("cyrillic") || langs.contains("ukrainian");
        boolean includeSymbols = langs.contains("symbols");

        List<CustomGlyphGroup> customGroups = new ArrayList<>();
        if (config.includeScannedGlyphs() && scannedGlyphs != null && !scannedGlyphs.isEmpty()) {
            Map<String, CustomGlyphGroup> grouped = new LinkedHashMap<>();
            for (Map.Entry<Integer, PackFontScanner.Glyph> entry : scannedGlyphs.entrySet()) {
                int cp = entry.getKey();
                PackFontScanner.Glyph g = entry.getValue();
                if (!g.defaultFont() || "space".equals(g.source()) || g.source() == null || g.source().isEmpty()) {
                    continue;
                }
                String key = g.source() + "|" + g.ascent() + "|" + g.height();
                CustomGlyphGroup group = grouped.computeIfAbsent(key, k ->
                    new CustomGlyphGroup(g.source(), g.ascent(), g.height())
                );
                group.addChar(cp);
            }
            customGroups.addAll(grouped.values());
        }

        String mode = config.mode() != null ? config.mode().toLowerCase() : "registered";
        boolean useRegistered =
            "registered".equals(mode) ||
            ("auto".equals(mode) && registeredEntries != null && !registeredEntries.isEmpty());
        if (useRegistered) {
            if (registeredEntries != null) {
                for (YFontRegistry.Entry entry : registeredEntries) {
                    HudAnchor anchor = entry.anchor();
                    if (anchor == null || anchor == HudAnchor.COMPASS_TAPE) {
                        continue;
                    }
                    emitFont(
                        anchor,
                        entry.y(),
                        includeEn,
                        includeUk,
                        includeSymbols,
                        nonlatinProvider,
                        accentedProvider,
                        asciiProvider,
                        customGroups,
                        outputFiles
                    );
                }
            }
            return;
        }

        int minY = config.minY();
        int maxY = config.maxY();
        int step = Math.max(1, config.step());

        // Generate font definitions for all 9 screen anchors
        for (HudAnchor anchor : HudAnchor.values()) {
            if (anchor == HudAnchor.COMPASS_TAPE) {
                continue;
            }
            for (int y = minY; y <= maxY; y += step) {
                emitFont(
                    anchor,
                    y,
                    includeEn,
                    includeUk,
                    includeSymbols,
                    nonlatinProvider,
                    accentedProvider,
                    asciiProvider,
                    customGroups,
                    outputFiles
                );
            }
        }
    }

    private static void emitFont(
        HudAnchor anchor,
        int y,
        boolean includeEn,
        boolean includeUk,
        boolean includeSymbols,
        JsonObject nonlatinProvider,
        JsonObject accentedProvider,
        JsonObject asciiProvider,
        List<CustomGlyphGroup> customGroups,
        Map<String, byte[]> outputFiles
    ) {
        int anchorId = anchor.id();
        int shaderId = anchorId + 1;
        int bit = 1024 + shaderId;
        int baseNumber = (bit << 13) + 4105;

        JsonArray providers = new JsonArray();

        // Vanilla space widths. Without them the space falls back to an empty ascii.png cell,
        // which the client draws narrower than FontMetrics measures, and the HUD shifts.
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        JsonObject advances = new JsonObject();
        advances.addProperty(" ", 4);
        advances.addProperty("\u200c", 0);
        space.add("advances", advances);
        providers.add(space);

        if ((includeUk || includeSymbols) && nonlatinProvider != null) {
            JsonObject copy = nonlatinProvider.deepCopy();
            copy.addProperty("ascent", -(baseNumber + y - 7));
            providers.add(copy);
        }

        if (includeUk && accentedProvider != null) {
            JsonObject copy = accentedProvider.deepCopy();
            copy.addProperty("ascent", -(baseNumber + y - 10));
            providers.add(copy);
        }

        if (includeEn && asciiProvider != null) {
            JsonObject copy = asciiProvider.deepCopy();
            copy.addProperty("ascent", -(baseNumber + y - 7));
            providers.add(copy);
        }

        for (CustomGlyphGroup group : customGroups) {
            JsonObject p = new JsonObject();
            p.addProperty("type", "bitmap");
            p.addProperty("file", group.file);
            p.addProperty("ascent", -(baseNumber + y - group.baseAscent));
            p.addProperty("height", group.height);
            JsonArray chars = new JsonArray();
            chars.add(group.charsString());
            p.add("chars", chars);
            providers.add(p);
        }

        JsonObject root = new JsonObject();
        root.add("providers", providers);
        byte[] bytes = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);

        outputFiles.put("assets/pixelay/font/hud_" + anchorId + "_" + y + ".json", bytes);
        if (y < 0) {
            outputFiles.put("assets/pixelay/font/hud_" + anchorId + "_m" + Math.abs(y) + ".json", bytes);
        }

        if (anchorId == 0) {
            outputFiles.put("assets/pixelay/font/y_" + y + ".json", bytes);
            if (y < 0) {
                outputFiles.put("assets/pixelay/font/y_m" + Math.abs(y) + ".json", bytes);
            }
        }
    }

    private static JsonObject loadTemplate() {
        try (InputStream in = YFontGenerator.class.getClassLoader().getResourceAsStream(TEMPLATE_PATH)) {
            if (in != null) {
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception ignored) {}

        // Fallback minimal template if file is not on classpath
        JsonObject root = new JsonObject();
        JsonArray providers = new JsonArray();

        JsonObject ascii = new JsonObject();
        ascii.addProperty("type", "bitmap");
        ascii.addProperty("file", "minecraft:font/ascii.png");
        ascii.addProperty("ascent", 7);
        JsonArray asciiChars = new JsonArray();
        asciiChars.add(
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"
        );
        asciiChars.add(
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"
        );
        asciiChars.add(" !\"#$%&'()*+,-./");
        asciiChars.add("0123456789:;<=>?");
        asciiChars.add("@ABCDEFGHIJKLMNO");
        asciiChars.add("PQRSTUVWXYZ[\\]^_");
        asciiChars.add("'abcdefghijklmno");
        asciiChars.add("pqrstuvwxyz{|}~\u0000");
        ascii.add("chars", asciiChars);
        providers.add(ascii);

        root.add("providers", providers);
        return root;
    }

    private static final class CustomGlyphGroup {

        final String file;
        final int baseAscent;
        final int height;
        final StringBuilder chars = new StringBuilder();

        CustomGlyphGroup(String file, int baseAscent, int height) {
            this.file = Objects.requireNonNull(file, "file");
            this.baseAscent = baseAscent;
            this.height = height > 0 ? height : 8;
        }

        void addChar(int codePoint) {
            chars.appendCodePoint(codePoint);
        }

        String charsString() {
            return chars.toString();
        }
    }
}
