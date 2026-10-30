package dev.horuse.pixelay.pack;

import dev.horuse.pixelay.config.PixelaySettings;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * The resource pack Pixelay builds for itself, into {@code plugins/Pixelay/pixelay-pack.zip}, on every start
 * and {@code /pixelay reload}: the core shaders of the screen cursor and of clipped canvas texts (CSS
 * {@code overflow}), and the cursor atlas made of the cursor sprites.
 * <p>
 * Every file of {@code plugins/Pixelay/pack/} goes into the pack too, at the same path, and replaces
 * Pixelay's own file there: {@code pack/cursors/pointer.png} is another pointer sprite,
 * {@code pack/assets/minecraft/...} any other asset. Sprites ({@code cursors/<name>.png}) are not copied as
 * they are: they are packed into the atlas the shader draws from.
 */
public final class PixelayPack {

    /** Where the pack's own files are, inside the plugin jar. */
    private static final String BUILT_IN = "pack/";
    private static final String CURSORS = "cursors/";
    private static final String ATLAS = "assets/minecraft/textures/misc/underwater.png";
    private static final String CONFIG = "assets/minecraft/shaders/include/pixelay_cursor_config.glsl";

    /** Atlas of 8x8 cells of 32 px: the sprites in the first row, the water tile next, a marker pixel last. */
    static final int ATLAS_SIZE = 256;
    static final int CELL = 32;
    static final int COLUMNS = ATLAS_SIZE / CELL;
    static final int WATER_CELL = 8;
    /** The atlas' last pixel: tells the shader it samples Pixelay's atlas. */
    private static final int MARKER = 0xFF504958;
    /** Sprite index 7 means "hidden" to the shader: at most 7 sprites. */
    public static final int MAX_SPRITES = 7;
    // STATE_STEP, LEVELS, TAN_MIN and TAN_MAX are also written in pixelay_state.glsl: change both together.

    /**
     * World age ticks per state of a screen session: the client counts the age on between two packets, the
     * shader rounds up to half of it away (about 0.8 s of lag).
     */
    public static final int STATE_STEP = 32;
    /** Levels of the canvas size in the state: {@code 1 + 7 + 8 * (levels - 1)} states of 32 ticks fit a day. */
    public static final int LEVELS = 93;
    /** Range of {@code tan(half the canvas height as seen)} the states cover, log-spaced. */
    public static final double TAN_MIN = 0.12;
    public static final double TAN_MAX = 3.0;

    private static volatile Built current;

    private PixelayPack() {}

    /**
     * The last built pack.
     *
     * @param zip the pack file
     * @param sha1 its SHA-1, hex (what a resource pack request needs)
     * @param sprites the cursor sprites in the atlas, index = sprite number of the shader
     */
    public record Built(Path zip, String sha1, List<CursorSprite> sprites) {
        /** The sprite number of a cursor name, or of {@code "default"} (0 when there is none) for an unknown one. */
        public int spriteIndex(String name) {
            int fallback = 0;
            for (int i = 0; i < sprites.size(); i++) {
                if (sprites.get(i).name().equals(name)) {
                    return i;
                }
                if (sprites.get(i).name().equals("default")) {
                    fallback = i;
                }
            }
            return fallback;
        }
    }

    /** The last built pack, or {@code null} when none was built (disabled, or the build failed). */
    public static Built current() {
        return current;
    }

    /**
     * Builds the pack into {@code dataFolder/pixelay-pack.zip}.
     *
     * @param pluginJar the plugin's jar, holding the built-in files
     */
    public static Built build(File pluginJar, Path dataFolder, PixelaySettings.ResourcePack settings, Logger log)
        throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        readBuiltIn(pluginJar, files);
        Path overrides = dataFolder.resolve("pack");
        Files.createDirectories(overrides.resolve(CURSORS));
        readOverrides(overrides, files);

        List<CursorSprite> sprites = settings.sprites();
        if (sprites.size() > MAX_SPRITES) {
            log.warning("generated-pack.cursor.sprites: at most " + MAX_SPRITES + " sprites, the rest is ignored");
            sprites = sprites.subList(0, MAX_SPRITES);
        }
        List<BufferedImage> images = new ArrayList<>();
        for (CursorSprite sprite : sprites) {
            images.add(sprite(files, sprite.name(), log));
        }
        files.put(ATLAS, png(atlas(images, image(files, CURSORS + "water.png"))));
        files.put(CONFIG, config(sprites, images, settings.cursorScale()).getBytes(StandardCharsets.UTF_8));
        files.put("assets/pixelay/font/transition.json", transitionFontJson().getBytes(StandardCharsets.UTF_8));
        files.put("assets/pixelay/textures/font/white.png", png(whitePixelImage()));
        // The sprites live in the atlas now.
        files.keySet().removeIf(path -> path.startsWith(CURSORS));

        Path zip = dataFolder.resolve("pixelay-pack.zip");
        byte[] bytes = zip(files);
        Files.write(zip, bytes);
        current = new Built(zip, sha1(bytes), List.copyOf(sprites));
        return current;
    }

    private static void readBuiltIn(File pluginJar, Map<String, byte[]> files) throws IOException {
        try (ZipFile jar = new ZipFile(pluginJar)) {
            for (ZipEntry entry : Collections.list(jar.entries())) {
                if (!entry.isDirectory() && entry.getName().startsWith(BUILT_IN)) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        files.put(entry.getName().substring(BUILT_IN.length()), in.readAllBytes());
                    }
                }
            }
        }
    }

    private static void readOverrides(Path root, Map<String, byte[]> files) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String path = root.relativize(file).toString().replace(File.separatorChar, '/');
                if (!path.endsWith(".DS_Store")) {
                    files.put(path, Files.readAllBytes(file));
                }
            }
        }
    }

    /** A sprite image, at most one cell: a bigger one is skipped (with a warning) and draws nothing. */
    private static BufferedImage sprite(Map<String, byte[]> files, String name, Logger log) throws IOException {
        BufferedImage image = image(files, CURSORS + name + ".png");
        if (image == null) {
            log.warning("Cursor sprite '" + name + "' has no image: add pack/cursors/" + name + ".png");
            return null;
        }
        if (image.getWidth() > CELL || image.getHeight() > CELL) {
            log.warning("Cursor sprite '" + name + "' is bigger than " + CELL + "x" + CELL + " px, skipped");
            return null;
        }
        return image;
    }

    private static BufferedImage image(Map<String, byte[]> files, String path) throws IOException {
        byte[] bytes = files.get(path);
        return bytes != null ? ImageIO.read(new ByteArrayInputStream(bytes)) : null;
    }

    private static BufferedImage atlas(List<BufferedImage> sprites, BufferedImage water) {
        BufferedImage atlas = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < sprites.size(); i++) {
            copy(sprites.get(i), atlas, i);
        }
        if (water != null) {
            // Stretched to the cell when it is smaller: the shader repeats the whole cell.
            for (int y = 0; y < CELL; y++) {
                for (int x = 0; x < CELL; x++) {
                    int argb = water.getRGB((x * water.getWidth()) / CELL, (y * water.getHeight()) / CELL);
                    atlas.setRGB(cellX(WATER_CELL) + x, cellY(WATER_CELL) + y, argb);
                }
            }
        }
        atlas.setRGB(ATLAS_SIZE - 1, ATLAS_SIZE - 1, MARKER);
        return atlas;
    }

    private static void copy(BufferedImage sprite, BufferedImage atlas, int cell) {
        if (sprite == null) {
            return;
        }
        for (int y = 0; y < sprite.getHeight(); y++) {
            for (int x = 0; x < sprite.getWidth(); x++) {
                atlas.setRGB(cellX(cell) + x, cellY(cell) + y, sprite.getRGB(x, y));
            }
        }
    }

    private static int cellX(int cell) {
        return (cell % COLUMNS) * CELL;
    }

    private static int cellY(int cell) {
        return (cell / COLUMNS) * CELL;
    }

    /** The constants the shader shares with the plugin, and every sprite's size and hot spot. */
    private static String config(List<CursorSprite> sprites, List<BufferedImage> images, double scale) {
        StringBuilder out = new StringBuilder();
        out.append("// Generated by Pixelay on every start and /pixelay reload: do not edit.\n");
        define(out, "PIXELAY_ATLAS_SIZE", ATLAS_SIZE + ".0");
        define(out, "PIXELAY_CELL", CELL + ".0");
        define(out, "PIXELAY_COLUMNS", COLUMNS + ".0");
        define(out, "PIXELAY_WATER_CELL", WATER_CELL + ".0");
        define(
            out,
            "PIXELAY_MARKER",
            ((MARKER >> 16) & 0xFF) + ", " + ((MARKER >> 8) & 0xFF) + ", " + (MARKER & 0xFF) + ", " + (MARKER >>> 24)
        );
        define(out, "PIXELAY_SPRITE_COUNT", String.valueOf(sprites.size()));
        define(out, "PIXELAY_CURSOR_SCALE", decimal(scale));
        // Per sprite: width, height (0 when it has no image), hot spot x, y.
        out.append("const vec4 PIXELAY_SPRITES[8] = vec4[8](");
        for (int i = 0; i < 8; i++) {
            BufferedImage image = i < images.size() ? images.get(i) : null;
            CursorSprite sprite = i < sprites.size() ? sprites.get(i) : null;
            out.append(i == 0 ? "" : ", ");
            if (image == null || sprite == null) {
                out.append("vec4(0.0)");
            } else {
                out.append(
                    String.format(
                        Locale.ROOT,
                        "vec4(%d.0, %d.0, %s, %s)",
                        image.getWidth(),
                        image.getHeight(),
                        decimal(sprite.hotSpotX()),
                        decimal(sprite.hotSpotY())
                    )
                );
            }
        }
        out.append(");\n");
        return out.toString();
    }

    private static void define(StringBuilder out, String name, String value) {
        out.append("#define ").append(name).append(' ').append(value).append('\n');
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** A zip with fixed entry times, so the same files always give the same SHA-1. */
    private static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String transitionFontJson() {
        return """
        {
          "providers": [
            {
              "type": "bitmap",
              "file": "pixelay:font/white.png",
              "ascent": 7,
              "height": 8,
              "chars": [
                "\\uE000"
              ]
            }
          ]
        }
        """;
    }

    private static BufferedImage whitePixelImage() {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        int white = 0xFFFFFFFF;
        img.setRGB(0, 0, white);
        img.setRGB(0, 1, white);
        img.setRGB(1, 0, white);
        img.setRGB(1, 1, white);
        return img;
    }
}
