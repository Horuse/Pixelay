package dev.horuse.pixelay.layout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.components.TextPlacement;
import dev.horuse.pixelay.config.PixelaySettings;
import dev.horuse.pixelay.content.FontMetrics;
import dev.horuse.pixelay.content.GlyphBox;
import dev.horuse.pixelay.content.LineAlign;
import dev.horuse.pixelay.data.Rect;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Draws a Pixelay layout to a PNG image without a Minecraft server.
 * The preview uses the same layout pass and the same text placement code as the in-game canvas.
 * It applies overflow clips, paint order, border widths and per-glyph text colors.
 * Glyph shapes come from a Java font that is reduced to the vanilla pixel grid.
 * Icon glyphs come from the bitmap providers of an optional resource pack.
 */
public final class LayoutPreview {

    /** Default advance of the icon glyphs when the pack has no data. */
    private static final int DEFAULT_ASCENT = 7;

    /** Default text color of a TextDisplay entity. */
    private static final int DEFAULT_TEXT_RGB = 0xFFFFFF;

    /** Maximum image side in pixels that the automatic scale aims for. */
    private static final int TARGET_IMAGE_SIDE = 1000;

    private static final Map<Integer, IconGlyph> ICONS = new HashMap<>();
    private static final Map<String, BufferedImage> GLYPH_CACHE = new HashMap<>();
    private static String loadedPack;
    private static boolean vanillaLoaded;

    private LayoutPreview() {}

    /**
     * Options for one preview image.
     *
     * @param scale image pixels per canvas pixel. A value of 0 or less selects an automatic scale.
     * @param backdropArgb color behind the canvas (the world in-game). A value of 0 draws a gray checkerboard.
     * @param packZip path of the resource pack that holds the icon glyphs, or null
     * @param vanillaDir folder with the vanilla font textures, or null
     */
    public record Options(double scale, int backdropArgb, Path packZip, Path vanillaDir) {
        public static Options defaults() {
            return new Options(0, 0, null, null);
        }

        /** Sets the folder with the vanilla font files (assets/minecraft/font and textures/font). */
        public Options withVanilla(Path dir) {
            return new Options(scale, backdropArgb, packZip, dir);
        }

        public Options withBackdrop(int argb) {
            return new Options(scale, argb, packZip, vanillaDir);
        }

        public Options withPack(Path zip) {
            return new Options(scale, backdropArgb, zip, vanillaDir);
        }

        public Options withScale(double newScale) {
            return new Options(newScale, backdropArgb, packZip, vanillaDir);
        }
    }

    /** One bitmap icon of the resource pack. */
    private record IconGlyph(BufferedImage image, int ascent, int height, int advance, boolean registered) {
        /** True if the glyph is drawn outside the normal line box and Pixelay shifts its box. */
        boolean verticalShift() {
            int top = 7 - ascent;
            return registered && (top < 0 || top + height > 9);
        }
    }

    /** Renders a layout to an image with default options. */
    public static BufferedImage renderToImage(Box<?> root, int pxW, int pxH) {
        return renderToImage(root, pxW, pxH, Options.defaults());
    }

    /** Renders a layout to an image. The canvas size is {@code pxW} by {@code pxH} canvas pixels. */
    public static BufferedImage renderToImage(Box<?> root, int pxW, int pxH, Options options) {
        ensureHeadlessSettings();
        loadVanilla(options.vanillaDir());
        loadPack(options.packZip());

        root.invalidate();
        root.arrange(new Rect(0, 0, pxW, pxH));
        assignClips(root, new Rect(0, 0, pxW, pxH));

        double scale =
            options.scale() > 0
                ? options.scale()
                : Math.max(1, Math.round((double) TARGET_IMAGE_SIDE / Math.max(pxW, pxH)));
        int imgW = (int) Math.max(1, Math.round(pxW * scale));
        int imgH = (int) Math.max(1, Math.round(pxH * scale));
        BufferedImage image = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        paintBackdrop(g, imgW, imgH, options.backdropArgb());
        g.scale(scale, scale);

        List<Box<?>> boxes = new ArrayList<>();
        root.collectBoxes(boxes);
        boxes.sort(Comparator.comparingInt(Box::zIndex));
        for (Box<?> box : boxes) {
            if (box.isHidden()) {
                continue;
            }
            paintBox(g, box);
        }
        g.dispose();
        return image;
    }

    /** Renders a layout to a PNG file with default options. */
    public static File renderToPng(Box<?> root, int pxW, int pxH, File outputFile) throws IOException {
        return renderToPng(root, pxW, pxH, Options.defaults(), outputFile);
    }

    /** Renders a layout to a PNG file. */
    public static File renderToPng(Box<?> root, int pxW, int pxH, Options options, File outputFile) throws IOException {
        BufferedImage image = renderToImage(root, pxW, pxH, options);
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }
        ImageIO.write(image, "PNG", outputFile);
        return outputFile;
    }

    /** Binds default settings when no server runs Pixelay. */
    private static void ensureHeadlessSettings() {
        try {
            Pixelay.settings();
        } catch (IllegalStateException notRunning) {
            Pixelay.bindHeadlessSettings(defaultSettings());
        }
    }

    private static PixelaySettings defaultSettings() {
        PixelaySettings.MetadataIndexes meta = new PixelaySettings.MetadataIndexes(
            8,
            9,
            10,
            11,
            12,
            13,
            14,
            15,
            16,
            17,
            23,
            24,
            25,
            26,
            27,
            8,
            9,
            23,
            24,
            23
        );
        return new PixelaySettings(
            false,
            false,
            20,
            40,
            new PixelaySettings.Text(0f, 500f, true, 200, false, false, 0),
            new PixelaySettings.Clicks(250, true, 0.25, 6),
            new PixelaySettings.Cursor("+", 17, false),
            new PixelaySettings.Layers(0.002f),
            new PixelaySettings.ChatSuppression(false, 100, 20),
            new PixelaySettings.Packets(1_000_000_000, meta),
            List.of(),
            new PixelaySettings.ResourcePack(false, 1.5, List.of())
        );
    }

    /** Calculates the visible area of each box like the in-game layout does. */
    private static void assignClips(Box<?> box, Rect clip) {
        Rect effective = box.isHidden() ? Rect.EMPTY : clip;
        box.clip(effective);
        if (box instanceof Container<?> container) {
            Rect inside = box.isHidden() ? Rect.EMPTY : box.childClip();
            for (Box<?> child : container.children) {
                assignClips(child, inside);
            }
        }
    }

    private static void paintBackdrop(Graphics2D g, int w, int h, int argb) {
        if (argb != 0) {
            g.setColor(new Color(argb, true));
            g.fillRect(0, 0, w, h);
            return;
        }
        int cell = 16;
        for (int y = 0; y < h; y += cell) {
            for (int x = 0; x < w; x += cell) {
                boolean dark = (x / cell + y / cell) % 2 == 0;
                g.setColor(dark ? new Color(0x3A3A3A) : new Color(0x464646));
                g.fillRect(x, y, cell, cell);
            }
        }
    }

    private static void paintBox(Graphics2D g, Box<?> box) {
        Rect clip = box.clip();
        Rect r = box.rect();
        if (clip == null || clip.width() <= 0 || clip.height() <= 0) {
            return;
        }
        g.setClip(new Rectangle2D.Double(clip.x(), clip.y(), clip.width(), clip.height()));

        int bg = box.effectiveBackground();
        if (r.width() > 0 && r.height() > 0 && bg >>> 24 != 0) {
            g.setColor(new Color(bg, true));
            g.fill(new Rectangle2D.Double(r.x(), r.y(), r.width(), r.height()));
        }

        int bc = box.effectiveBorderColor();
        Insets b = box.effectiveBorderWidths();
        if (bc >>> 24 != 0 && (b.top() > 0 || b.right() > 0 || b.bottom() > 0 || b.left() > 0)) {
            g.setColor(new Color(bc, true));
            g.fill(new Rectangle2D.Double(r.x(), r.y(), r.width(), b.top()));
            g.fill(new Rectangle2D.Double(r.x(), r.bottom() - b.bottom(), r.width(), b.bottom()));
            g.fill(new Rectangle2D.Double(r.x(), r.y(), b.left(), r.height()));
            g.fill(new Rectangle2D.Double(r.right() - b.right(), r.y(), b.right(), r.height()));
        }

        if (box instanceof Text textBox) {
            paintText(g, textBox.component());
        } else if (box instanceof Button buttonBox) {
            paintText(g, buttonBox.component());
        } else if (box instanceof Item) {
            paintItemPlaceholder(g, r);
        }
        g.setClip(null);
    }

    private static void paintItemPlaceholder(Graphics2D g, Rect r) {
        g.setColor(new Color(0x38BDF8, false));
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
        g.fill(new Rectangle2D.Double(r.x(), r.y(), r.width(), r.height()));
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        g.setColor(new Color(0x38BDF8));
        g.draw(new Rectangle2D.Double(r.x(), r.y(), r.width() - 1, r.height() - 1));
        g.drawLine((int) r.x(), (int) r.y(), (int) r.right() - 1, (int) r.bottom() - 1);
        g.drawLine((int) r.right() - 1, (int) r.y(), (int) r.x(), (int) r.bottom() - 1);
    }

    private static void paintText(Graphics2D g, AbstractCanvasComponent component) {
        String raw = component.renderText();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        TextPlacement.Placement placement = TextPlacement.place(component, raw);
        LineAlign align = switch (component.hAlign()) {
            case LEFT -> LineAlign.LEFT;
            case CENTER -> LineAlign.CENTER;
            case RIGHT -> LineAlign.RIGHT;
        };
        List<GlyphBox> boxes = placement.laid().glyphBoxes(align);
        List<GlyphStyle> styles = new ArrayList<>();
        collectStyles(placement.laid().source(), DEFAULT_TEXT_RGB, false, styles);

        double fontPx = placement.fontPx();
        for (int i = 0; i < boxes.size() && i < styles.size(); i++) {
            GlyphBox gb = boxes.get(i);
            if (gb == null) {
                continue;
            }
            GlyphStyle style = styles.get(i);
            double left = placement.textLeft() + gb.left() * fontPx;
            double top = placement.firstLineTop() + gb.top() * fontPx;
            drawGlyph(g, style, gb, left, top, fontPx);
        }
    }

    /** The style of one glyph. */
    private record GlyphStyle(int codePoint, int rgb, boolean bold) {}

    /** Collects glyph styles in the order of the layout glyph list. */
    private static void collectStyles(Component component, int inheritedRgb, boolean parentBold, List<GlyphStyle> out) {
        TextColor color = component.style().color();
        int rgb = color != null ? color.value() : inheritedRgb;
        TextDecoration.State state = component.style().decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.TRUE || (state != TextDecoration.State.FALSE && parentBold);
        if (component instanceof TextComponent text) {
            String content = text.content();
            for (int i = 0; i < content.length(); ) {
                int cp = content.codePointAt(i);
                out.add(new GlyphStyle(cp, rgb, bold));
                i += Character.charCount(cp);
            }
        }
        for (Component child : component.children()) {
            collectStyles(child, rgb, bold, out);
        }
    }

    private static void drawGlyph(Graphics2D g, GlyphStyle style, GlyphBox gb, double left, double top, double fontPx) {
        int inkW = Math.max(1, (int) Math.round(gb.width()));
        IconGlyph icon = ICONS.get(style.codePoint());
        if (icon != null) {
            double cellScale = (double) icon.height() / icon.image().getHeight();
            double lineTopPx = top - (icon.verticalShift() ? (7 - icon.ascent()) * fontPx : 0);
            double spriteTop = lineTopPx + (7 - icon.ascent()) * fontPx;
            double px = cellScale * fontPx;
            int passes = style.bold() ? 2 : 1;
            for (int pass = 0; pass < passes; pass++) {
                double dx = left + pass * fontPx;
                for (int y = 0; y < icon.image().getHeight(); y++) {
                    for (int x = 0; x < icon.image().getWidth(); x++) {
                        int argb = icon.image().getRGB(x, y);
                        if (argb >>> 24 < 128) {
                            continue;
                        }
                        int r = (((argb >> 16) & 0xFF) * ((style.rgb() >> 16) & 0xFF)) / 255;
                        int gr = (((argb >> 8) & 0xFF) * ((style.rgb() >> 8) & 0xFF)) / 255;
                        int b = ((argb & 0xFF) * (style.rgb() & 0xFF)) / 255;
                        g.setColor(new Color(r, gr, b));
                        g.fill(new Rectangle2D.Double(dx + x * px, spriteTop + y * px, px, px));
                    }
                }
            }
            return;
        }
        String decomposed = java.text.Normalizer.normalize(
            new String(Character.toChars(style.codePoint())),
            java.text.Normalizer.Form.NFD
        );
        if (
            decomposed.length() == 2 && decomposed.charAt(1) == 0x0306 && ICONS.containsKey((int) decomposed.charAt(0))
        ) {
            // The vanilla bitmap font has no breve letters. Draw the base letter and a small breve above it.
            drawGlyph(g, new GlyphStyle(decomposed.charAt(0), style.rgb(), style.bold()), gb, left, top, fontPx);
            int ink = ICONS.get((int) decomposed.charAt(0)).advance() - 1;
            int firstRow = Character.isUpperCase(style.codePoint()) ? -2 : 0;
            g.setColor(new Color(style.rgb()));
            g.fill(new Rectangle2D.Double(left, top + firstRow * fontPx, fontPx, fontPx));
            g.fill(new Rectangle2D.Double(left + (ink - 1) * fontPx, top + firstRow * fontPx, fontPx, fontPx));
            g.fill(
                new Rectangle2D.Double(
                    left + fontPx,
                    top + (firstRow + 1) * fontPx,
                    Math.max(1, ink - 2) * fontPx,
                    fontPx
                )
            );
            return;
        }
        BufferedImage bitmap = glyphBitmap(style.codePoint(), inkW, style.bold());
        g.setColor(new Color(style.rgb()));
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getRGB(x, y) >>> 24 > 127) {
                    g.fill(new Rectangle2D.Double(left + x * fontPx, top + y * fontPx, fontPx, fontPx));
                }
            }
        }
    }

    /** Rasterizes one character on the font pixel grid. Width is {@code inkW}, height is 8 rows. */
    private static BufferedImage glyphBitmap(int codePoint, int inkW, boolean bold) {
        String key = codePoint + ":" + inkW + ":" + bold;
        BufferedImage cached = GLYPH_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int width = inkW + (bold ? 1 : 0);
        BufferedImage img = new BufferedImage(width, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg = img.createGraphics();
        gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        gg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 8);
        String text = new String(Character.toChars(codePoint));
        double natural = font.getStringBounds(text, new FontRenderContext(null, false, false)).getWidth();
        double sx = natural > 0 ? Math.max(0.4, Math.min(1.6, inkW / natural)) : 1;
        AffineTransform tx = AffineTransform.getScaleInstance(sx, 1);
        gg.setFont(font.deriveFont(tx));
        gg.setColor(Color.WHITE);
        gg.drawString(text, 0, 7);
        if (bold) {
            gg.drawString(text, 1, 7);
        }
        gg.dispose();
        GLYPH_CACHE.put(key, img);
        return img;
    }

    /** Loads bitmap icon glyphs of a resource pack and registers their advances. */
    private static synchronized void loadPack(Path zipPath) {
        if (zipPath == null) {
            return;
        }
        String id = zipPath.toAbsolutePath().toString();
        if (id.equals(loadedPack)) {
            return;
        }
        loadedPack = id;
        ICONS.keySet().removeIf(
            code -> (code >= 0xE000 && code <= 0xF8FF) || code < 0x20 || (code >= 0x80 && code <= 0x9F)
        );
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry("assets/minecraft/font/default.json");
            if (entry == null) {
                return;
            }
            JsonObject root;
            try (InputStream in = zip.getInputStream(entry)) {
                root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            for (JsonElement provider : root.getAsJsonArray("providers")) {
                registerProvider(zip, provider.getAsJsonObject());
            }
        } catch (IOException | RuntimeException ignored) {
            // A broken pack only removes the icons. The preview keeps working.
        }
    }

    private static void registerProvider(ZipFile zip, JsonObject provider) throws IOException {
        registerProvider(provider, true, (namespace, path) -> {
            ZipEntry png = zip.getEntry("assets/" + namespace + "/textures/" + path);
            if (png == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(png)) {
                return ImageIO.read(in);
            }
        });
    }

    /** Reads an image by namespace and texture path. */
    private interface SheetLoader {
        BufferedImage load(String namespace, String path) throws IOException;
    }

    /** Loads the vanilla bitmap font from a folder with the extracted client assets. */
    private static synchronized void loadVanilla(Path dir) {
        if (dir == null || vanillaLoaded) {
            return;
        }
        vanillaLoaded = true;
        try {
            Path include = dir.resolve("assets/minecraft/font/include/default.json");
            JsonObject root;
            try (InputStream in = java.nio.file.Files.newInputStream(include)) {
                root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            for (JsonElement provider : root.getAsJsonArray("providers")) {
                registerProvider(provider.getAsJsonObject(), false, (namespace, path) ->
                    ImageIO.read(dir.resolve("assets/" + namespace + "/textures/" + path).toFile())
                );
            }
        } catch (IOException | RuntimeException ignored) {
            // Without vanilla files the preview falls back to a Java font.
        }
    }

    private static void registerProvider(JsonObject provider, boolean updateMetrics, SheetLoader loader)
        throws IOException {
        if (!"bitmap".equals(provider.get("type").getAsString())) {
            return;
        }
        String file = provider.get("file").getAsString();
        int colon = file.indexOf(':');
        String namespace = colon >= 0 ? file.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? file.substring(colon + 1) : file;
        BufferedImage sheet = loader.load(namespace, path);
        if (sheet == null) {
            return;
        }
        int ascent = provider.has("ascent") ? provider.get("ascent").getAsInt() : DEFAULT_ASCENT;
        int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
        JsonArray rows = provider.getAsJsonArray("chars");
        int rowCount = rows.size();
        for (int row = 0; row < rowCount; row++) {
            int[] codes = rows.get(row).getAsString().codePoints().toArray();
            int cellW = sheet.getWidth() / Math.max(1, codes.length);
            int cellH = sheet.getHeight() / rowCount;
            for (int col = 0; col < codes.length; col++) {
                if (codes[col] == 0) {
                    continue;
                }
                BufferedImage cell = sheet.getSubimage(col * cellW, row * cellH, cellW, cellH);
                double scale = (double) height / cellH;
                int ink = inkWidth(cell);
                int advance = (int) Math.round(ink * scale) + 1;
                ICONS.put(codes[col], new IconGlyph(cell, ascent, height, advance, updateMetrics));
                if (updateMetrics) {
                    FontMetrics.setGlyph(codes[col], advance, ascent, height);
                }
            }
        }
    }

    /** Finds the width of the visible part of an icon cell. */
    private static int inkWidth(BufferedImage cell) {
        int last = 0;
        for (int x = 0; x < cell.getWidth(); x++) {
            for (int y = 0; y < cell.getHeight(); y++) {
                if (cell.getRGB(x, y) >>> 24 != 0) {
                    last = x + 1;
                    break;
                }
            }
        }
        return Math.max(1, last);
    }
}
