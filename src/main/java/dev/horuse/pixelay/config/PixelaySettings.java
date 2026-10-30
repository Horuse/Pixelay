package dev.horuse.pixelay.config;

import dev.horuse.pixelay.pack.CursorSprite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Immutable snapshot of {@code config.yml}. The plugin builds a snapshot on enable and reload.
 * Components read consistent values via {@link dev.horuse.pixelay.Pixelay#settings()}.
 */
public record PixelaySettings(
    boolean debug,
    boolean cleanupOnEnable,
    long refreshPeriodTicks,
    long healPeriodTicks,
    Text text,
    Clicks clicks,
    Cursor cursor,
    Layers layers,
    ChatSuppression chatSuppression,
    Packets packets,
    List<String> resourcePackOrder,
    ResourcePack resourcePack
) {
    /** Text rendering options shared by all renderers. */
    public record Text(
        float bottomGapFontPx,
        float viewRange,
        boolean fullBrightness,
        int lineWidth,
        boolean shadow,
        boolean seeThrough,
        int backgroundArgb
    ) {}

    /**
     * Click routing options.
     *
     * @param hitTileSize size in blocks of invisible click tiles on a canvas
     * @param maxDistance maximum distance in blocks for player canvas clicks
     */
    public record Clicks(long debounceMs, boolean blockSneakDismount, double hitTileSize, double maxDistance) {}

    /**
     * Look-at cursor appearance options.
     *
     * @param shader true if screens render the cursor with the core shader, false for TextDisplay entities
     */
    public record Cursor(String glyph, double heightPx, boolean shader) {}

    /**
     * Configuration options for the generated resource pack.
     *
     * @param cursorScale screen pixels per sprite pixel on a 1080p display
     * @param sprites list of cursor sprites in atlas order
     */
    public record ResourcePack(boolean enabled, double cursorScale, List<CursorSprite> sprites) {}

    /** @param step distance in blocks between drawing layers (see {@link dev.horuse.pixelay.data.Layers}) */
    public record Layers(float step) {}

    /** Chat suppression for full-screen canvases. */
    public record ChatSuppression(boolean enabled, int queueLimit, int deliverLimit) {}

    /**
     * Packet entity settings. Provides metadata indexes that change across Minecraft versions.
     */
    public record Packets(int fakeEntityIdBase, MetadataIndexes metadata) {}

    public record MetadataIndexes(
        int transformationStartDelta,
        int transformationDuration,
        int positionInterpolationDuration,
        int translation,
        int scale,
        int leftRotation,
        int rightRotation,
        int billboard,
        int brightnessOverride,
        int viewRange,
        int text,
        int lineWidth,
        int backgroundColor,
        int textOpacity,
        int styleFlags,
        int interactionWidth,
        int interactionHeight,
        int item,
        int itemDisplayTransform,
        int blockState
    ) {}

    /** Copies default config.yml if missing, then loads configuration into a snapshot. */
    public static PixelaySettings load(JavaPlugin plugin) throws ConfigurateException {
        ConfigurationNode root = YamlFiles.load(plugin, "config.yml");
        ConfigurationNode scheduler = root.node("scheduler");
        ConfigurationNode text = root.node("text");
        ConfigurationNode clicks = root.node("clicks");
        ConfigurationNode cursor = root.node("cursor");
        ConfigurationNode chat = root.node("chat-suppression");
        ConfigurationNode packets = root.node("packets");
        ConfigurationNode meta = packets.node("metadata");
        warnOutdated(plugin, root);
        return new PixelaySettings(
            root.node("debug").getBoolean(false),
            root.node("cleanup-on-enable").getBoolean(true),
            Math.max(1, scheduler.node("refresh-period-ticks").getLong(20)),
            Math.max(1, scheduler.node("heal-period-ticks").getLong(40)),
            new Text(
                text.node("bottom-gap-font-px").getFloat(0f),
                text.node("view-range").getFloat(500f),
                text.node("full-brightness").getBoolean(true),
                text.node("line-width").getInt(200),
                text.node("shadow").getBoolean(false),
                text.node("see-through").getBoolean(false),
                parseArgb(text.node("background").getString("#00000000"))
            ),
            new Clicks(
                Math.max(0, clicks.node("debounce-ms").getLong(150)),
                clicks.node("block-sneak-dismount").getBoolean(true),
                clicks.node("hit-tile-size").getDouble(0.25),
                clicks.node("max-distance").getDouble(6)
            ),
            new Cursor(
                cursor.node("glyph").getString("+"),
                cursor.node("height-px").getDouble(17),
                cursor.node("shader").getBoolean(true)
            ),
            new Layers((float) Math.max(0.0001, root.node("layers", "step").getDouble(0.002))),
            new ChatSuppression(
                chat.node("enabled").getBoolean(true),
                Math.max(0, chat.node("queue-limit").getInt(100)),
                Math.max(0, chat.node("deliver-limit").getInt(20))
            ),
            new Packets(
                packets.node("fake-entity-id-base").getInt(1_000_000_000),
                new MetadataIndexes(
                    meta.node("transformation-start-delta").getInt(8),
                    meta.node("transformation-duration").getInt(9),
                    meta.node("position-interpolation-duration").getInt(10),
                    meta.node("translation").getInt(11),
                    meta.node("scale").getInt(12),
                    meta.node("left-rotation").getInt(13),
                    meta.node("right-rotation").getInt(14),
                    meta.node("billboard").getInt(15),
                    meta.node("brightness-override").getInt(16),
                    meta.node("view-range").getInt(17),
                    meta.node("text").getInt(23),
                    meta.node("line-width").getInt(24),
                    meta.node("background-color").getInt(25),
                    meta.node("text-opacity").getInt(26),
                    meta.node("style-flags").getInt(27),
                    meta.node("interaction-width").getInt(8),
                    meta.node("interaction-height").getInt(9),
                    meta.node("item").getInt(23),
                    meta.node("item-display-transform").getInt(24),
                    meta.node("block-state").getInt(23)
                )
            ),
            root.node("resource-packs", "order").getList(String.class, List.of()),
            new ResourcePack(
                root.node("generated-pack", "enabled").getBoolean(true),
                Math.max(0.1, root.node("generated-pack", "cursor", "scale").getDouble(1.5)),
                cursorSprites(root.node("generated-pack", "cursor", "sprites"))
            )
        );
    }

    /** Parses cursor sprite mappings with names and hotspots in order. Returns defaults when empty. */
    private static List<CursorSprite> cursorSprites(ConfigurationNode node) throws ConfigurateException {
        if (!node.isMap()) {
            return List.of(
                new CursorSprite("default", 0.5, 0.5),
                new CursorSprite("pointer", 6, 0.5),
                new CursorSprite("text", 3.5, 8),
                new CursorSprite("not-allowed", 8.5, 8.5)
            );
        }
        List<CursorSprite> sprites = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            List<Double> hotSpot = entry.getValue().getList(Double.class, List.of(0.0, 0.0));
            sprites.add(
                new CursorSprite(
                    String.valueOf(entry.getKey()),
                    hotSpot.isEmpty() ? 0 : hotSpot.get(0),
                    hotSpot.size() < 2 ? 0 : hotSpot.get(1)
                )
            );
        }
        return sprites;
    }

    /** Current configuration format version. Older formats load with migration warnings. */
    private static final int CONFIG_VERSION = 2;

    /**
     * Warns about legacy configuration options from version 1.
     */
    private static void warnOutdated(JavaPlugin plugin, ConfigurationNode root) {
        if (root.node("config-version").getInt(1) >= CONFIG_VERSION) {
            return;
        }
        var log = plugin.getLogger();
        log.warning("config.yml is from an older Pixelay version; compare it with the default one:");
        if (!root.node("text", "normal-offset").virtual()) {
            log.warning("  text.normal-offset is no longer used: depths are set by layers.step");
        }
        if (!root.node("cursor", "depth-offset").virtual()) {
            log.warning("  cursor.depth-offset is no longer used: the cursor is always the top layer");
        }
        double gap = root.node("text", "bottom-gap-font-px").getDouble(0);
        if (gap != 0) {
            log.warning(
                "  text.bottom-gap-font-px is " +
                    gap +
                    ": it now applies to every alignment and 0 matches Minecraft 1.21.11 - set it to 0"
            );
        }
        log.warning("  then add config-version: " + CONFIG_VERSION + " to hide this message");
    }

    /** Parses color strings in {@code #AARRGGBB} or {@code #RRGGBB} format to packed ARGB integers. */
    static int parseArgb(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        String hex = value.trim().replace("#", "");
        long parsed = Long.parseLong(hex, 16);
        return hex.length() <= 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
    }
}
