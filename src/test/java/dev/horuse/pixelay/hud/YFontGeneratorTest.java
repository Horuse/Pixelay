package dev.horuse.pixelay.hud;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.horuse.pixelay.config.PixelaySettings;
import dev.horuse.pixelay.content.PackFontScanner;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link YFontGenerator} and {@link HudPosition}.
 */
class YFontGeneratorTest {

    @Test
    void generatesFilesForConfiguredRange() {
        PixelaySettings.FontYOffsets config = new PixelaySettings.FontYOffsets(
            true,
            -5,
            10,
            5,
            List.of("en", "uk", "symbols"),
            false
        );
        Map<String, byte[]> files = new HashMap<>();

        YFontGenerator.generate(config, Map.of(), files);

        // Expected Y values: -5, 0, 5, 10 -> 4 offsets
        // Negative offsets also generate y_m<abs>
        assertTrue(files.containsKey("assets/pixelay/font/y_-5.json"));
        assertTrue(files.containsKey("assets/pixelay/font/y_m5.json"));
        assertTrue(files.containsKey("assets/pixelay/font/y_0.json"));
        assertTrue(files.containsKey("assets/pixelay/font/y_5.json"));
        assertTrue(files.containsKey("assets/pixelay/font/y_10.json"));

        String jsonStr = new String(files.get("assets/pixelay/font/y_5.json"), StandardCharsets.UTF_8);
        JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
        assertTrue(json.has("providers"));
        assertTrue(json.getAsJsonArray("providers").size() >= 1);
    }

    @Test
    void includesCustomScannedGlyphs() {
        PixelaySettings.FontYOffsets config = new PixelaySettings.FontYOffsets(true, 0, 0, 1, List.of("en"), true);
        Map<Integer, PackFontScanner.Glyph> scanned = Map.of(
            0xE001,
            new PackFontScanner.Glyph(10, 8, 8, "test-pack", "custom:font/icon.png")
        );
        Map<String, byte[]> files = new HashMap<>();

        YFontGenerator.generate(config, scanned, files);

        String jsonStr = new String(files.get("assets/pixelay/font/y_0.json"), StandardCharsets.UTF_8);
        assertTrue(jsonStr.contains("custom:font/icon.png"));
        assertTrue(jsonStr.contains("\uE001"));
        assertTrue(jsonStr.contains("-8400897"));
    }

    @Test
    void hudPositionGeneratesExpectedKeysAndWidth() {
        Key key = HudPosition.font(15);
        assertEquals("pixelay", key.namespace());
        assertEquals("y_15", key.value());

        Key anchorKey = HudPosition.font(HudAnchor.TOP_RIGHT, 15);
        assertEquals("pixelay", anchorKey.namespace());
        assertEquals("hud_2_15", anchorKey.value());

        Key negativeKey = HudPosition.font(HudAnchor.BOTTOM_RIGHT, -16);
        assertEquals("pixelay", negativeKey.namespace());
        assertEquals("hud_8_m16", negativeKey.value());

        Component comp = Component.text("Test");
        int width = HudPosition.visualWidth(comp);
        assertTrue(width > 0);

        Component positioned = HudPosition.at(comp, 20, 15, width);
        assertNotNull(positioned);
    }
}
