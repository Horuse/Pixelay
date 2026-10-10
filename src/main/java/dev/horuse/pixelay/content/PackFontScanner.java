package dev.horuse.pixelay.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Computes glyph advances of the {@code minecraft:default} font from resource packs, the same way the
 * client does, so custom glyphs (icons, cursors) are measured exactly without manual numbers.
 * <p>
 * Packs are folders or zip files, given highest priority first; like the client's pack stack, the first
 * provider that defines a character wins. Supported providers: {@code bitmap} and {@code space};
 * {@code reference} is followed. Others ({@code ttf}, {@code unihex}) are skipped with a warning: the
 * built-in table already covers the vanilla font.
 * <p>
 * Bitmap rule: the glyph width is the rightmost column of its cell with a non-transparent pixel, plus
 * one (transparent columns on the left count); {@code advance = round(width * height / cellHeight) + 1}.
 */
public final class PackFontScanner {

    private static final String DEFAULT_FONT = "minecraft:default";

    /**
     * One computed glyph: its advance, where it is drawn ({@code ascent}/{@code height} as in its provider,
     * height 0 for spaces) and where it came from (for the generated file's comments).
     */
    public record Glyph(int advance, int ascent, int height, String pack, String source) {}

    private final List<Pack> packs = new ArrayList<>();
    private final Consumer<String> warn;

    private PackFontScanner(Consumer<String> warn) {
        this.warn = warn;
    }

    /**
     * @param packRoots pack folders or zip files, highest priority first
     * @return advances by code point, in provider order
     */
    public static Map<Integer, Glyph> scan(List<Path> packRoots, Consumer<String> warn) throws IOException {
        PackFontScanner scanner = new PackFontScanner(warn);
        try {
            for (Path root : packRoots) {
                scanner.packs.add(Pack.open(root));
            }
            return scanner.collect();
        } finally {
            for (Pack pack : scanner.packs) {
                pack.close();
            }
        }
    }

    private Map<Integer, Glyph> collect() {
        Map<Integer, Glyph> glyphs = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Pack pack : packs) {
            byte[] json = pack.read(fontPath(DEFAULT_FONT));
            if (json != null) {
                readFont(pack, DEFAULT_FONT, json, glyphs, seen);
            }
        }
        return glyphs;
    }

    private void readFont(Pack pack, String id, byte[] json, Map<Integer, Glyph> glyphs, Set<String> seen) {
        if (!seen.add(pack.name + "|" + id)) {
            return;
        }
        JsonObject doc;
        try {
            doc = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            warn.accept(pack.name + ": invalid font json " + id + ": " + e.getMessage());
            return;
        }
        JsonArray providers = doc.has("providers") ? doc.getAsJsonArray("providers") : new JsonArray();
        for (JsonElement element : providers) {
            JsonObject provider = element.getAsJsonObject();
            if (!active(provider)) {
                continue;
            }
            String type = string(provider, "type", "");
            try {
                switch (type) {
                    case "bitmap" -> bitmap(pack, provider, glyphs);
                    case "space" -> space(pack, provider, glyphs);
                    case "reference" -> reference(string(provider, "id", ""), glyphs, seen);
                    default -> warn.accept(
                        pack.name + ": " + id + ": provider '" + type + "' is not measured, skipped"
                    );
                }
            } catch (IOException | RuntimeException e) {
                warn.accept(pack.name + ": " + id + ": " + type + " provider skipped: " + e.getMessage());
            }
        }
    }

    /** A referenced font comes from the highest pack that has it. */
    private void reference(String id, Map<Integer, Glyph> glyphs, Set<String> seen) {
        for (Pack pack : packs) {
            byte[] json = pack.read(fontPath(id));
            if (json != null) {
                readFont(pack, id, json, glyphs, seen);
                return;
            }
        }
    }

    private void bitmap(Pack owner, JsonObject provider, Map<Integer, Glyph> glyphs) throws IOException {
        String file = string(provider, "file", null);
        if (file == null) {
            throw new IOException("no file");
        }
        BufferedImage image = readTexture(file);
        if (image == null) {
            throw new IOException("texture not found: " + file);
        }
        JsonArray rows = provider.getAsJsonArray("chars");
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int columns = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
        if (columns == 0) {
            return;
        }
        int cellWidth = image.getWidth() / columns;
        int cellHeight = image.getHeight() / rows.size();
        int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
        int ascent = provider.get("ascent").getAsInt();
        double scale = (double) height / cellHeight;
        boolean alpha = image.getColorModel().hasAlpha();

        for (int row = 0; row < rows.size(); row++) {
            int[] codePoints = rows.get(row).getAsString().codePoints().toArray();
            for (int column = 0; column < codePoints.length; column++) {
                int codePoint = codePoints[column];
                if (codePoint == 0 || glyphs.containsKey(codePoint)) {
                    continue;
                }
                int width = inkWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight, alpha);
                int advance = (int) (0.5 + width * scale) + 1;
                glyphs.put(codePoint, new Glyph(advance, ascent, height, owner.name, file));
            }
        }
    }

    private static void space(Pack owner, JsonObject provider, Map<Integer, Glyph> glyphs) {
        JsonObject advances = provider.getAsJsonObject("advances");
        if (advances == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : advances.entrySet()) {
            String key = entry.getKey();
            if (key.codePointCount(0, key.length()) != 1) {
                continue;
            }
            int codePoint = key.codePointAt(0);
            if (!glyphs.containsKey(codePoint)) {
                glyphs.put(
                    codePoint,
                    new Glyph((int) Math.round(entry.getValue().getAsDouble()), 0, 0, owner.name, "space")
                );
            }
        }
    }

    /** Rightmost cell column with any visible pixel, plus one (0 for an empty cell). */
    private static int inkWidth(BufferedImage image, int x0, int y0, int width, int height, boolean alpha) {
        for (int dx = width - 1; dx >= 0; dx--) {
            for (int dy = 0; dy < height; dy++) {
                int argb = image.getRGB(x0 + dx, y0 + dy);
                // The client checks alpha, or luminance for images without an alpha channel.
                int value = alpha ? argb >>> 24 : (argb >> 16) & 0xFF;
                if (value != 0) {
                    return dx + 1;
                }
            }
        }
        return 0;
    }

    /** A texture comes from the highest pack that has it. */
    private BufferedImage readTexture(String location) throws IOException {
        String path = assetPath(location, "textures/");
        for (Pack pack : packs) {
            byte[] bytes = pack.read(path);
            if (bytes != null) {
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
        }
        return null;
    }

    /** Providers with a {@code filter} apply only with the default client options (all off). */
    private static boolean active(JsonObject provider) {
        JsonObject filter = provider.getAsJsonObject("filter");
        if (filter == null) {
            return true;
        }
        for (Map.Entry<String, JsonElement> option : filter.entrySet()) {
            if (option.getValue().getAsBoolean()) {
                return false;
            }
        }
        return true;
    }

    private static String fontPath(String id) {
        return assetPath(id, "font/") + ".json";
    }

    /** {@code ns:path} → {@code assets/ns/<prefix>path}. */
    private static String assetPath(String location, String prefix) {
        int colon = location.indexOf(':');
        String namespace = colon >= 0 ? location.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? location.substring(colon + 1) : location;
        return "assets/" + namespace + "/" + prefix + path;
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    /** A pack folder or zip; paths are relative to the folder with {@code pack.mcmeta}. */
    private abstract static class Pack implements Closeable {

        final String name;

        Pack(String name) {
            this.name = name;
        }

        abstract byte[] read(String path);

        static Pack open(Path root) throws IOException {
            String name = root.getFileName().toString();
            if (Files.isDirectory(root)) {
                return new FolderPack(name, root);
            }
            return new ZipPack(name, new ZipFile(root.toFile()));
        }

        @Override
        public void close() throws IOException {}
    }

    private static final class FolderPack extends Pack {

        private final Path root;

        FolderPack(String name, Path root) {
            super(name);
            this.root = root;
        }

        @Override
        byte[] read(String path) {
            Path file = root.resolve(path);
            try {
                return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
            } catch (IOException e) {
                return null;
            }
        }
    }

    private static final class ZipPack extends Pack {

        private final ZipFile zip;
        /** Zips often wrap the pack in one top folder: entries are read below it. */
        private final String prefix;

        ZipPack(String name, ZipFile zip) {
            super(name);
            this.zip = zip;
            this.prefix = findPrefix(zip);
        }

        private static String findPrefix(ZipFile zip) {
            if (zip.getEntry("pack.mcmeta") != null) {
                return "";
            }
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement().getName();
                if (entry.endsWith("/pack.mcmeta") && entry.indexOf('/') == entry.lastIndexOf('/')) {
                    return entry.substring(0, entry.length() - "pack.mcmeta".length());
                }
            }
            return "";
        }

        @Override
        byte[] read(String path) {
            ZipEntry entry = zip.getEntry(prefix + path);
            if (entry == null || entry.isDirectory()) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public void close() throws IOException {
            zip.close();
        }
    }
}
