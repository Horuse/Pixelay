package dev.horuse.pixelay.config;

import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Immutable snapshot of {@code config.yml}. A fresh snapshot is built on every enable/reload, so
 * components always read consistent values via {@link dev.horuse.pixelay.Pixelay#settings()}.
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
    List<String> resourcePackOrder
) {
    /** Text rendering options shared by every renderer. */
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
     * @param hitTileSize size (blocks) of the invisible click tiles laid over a canvas
     * @param maxDistance farthest (blocks) a player may click a canvas from
     */
    public record Clicks(long debounceMs, boolean blockSneakDismount, double hitTileSize, double maxDistance) {}

    /** Look-at cursor appearance. */
    public record Cursor(String glyph, double heightPx) {}

    /** @param step distance (blocks) between two drawing layers, see {@link dev.horuse.pixelay.data.Layers} */
    public record Layers(float step) {}

    /** Chat suppression for full-screen canvases. */
    public record ChatSuppression(boolean enabled, int queueLimit, int deliverLimit) {}

    /**
     * Fake entity settings. Display/TextDisplay metadata indexes change between Minecraft versions,
     * so they are exposed here instead of being hard-coded.
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

    /** Copies the default config.yml if missing, then parses it into a fresh snapshot. */
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
            new Cursor(cursor.node("glyph").getString("+"), cursor.node("height-px").getDouble(17)),
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
            root.node("resource-packs", "order").getList(String.class, List.of())
        );
    }

    /** Current config.yml layout; older files still load, with a warning for every changed option. */
    private static final int CONFIG_VERSION = 2;

    /**
     * Version 2 replaced {@code text.normal-offset} and {@code cursor.depth-offset} with {@code layers.step},
     * and the meaning of {@code text.bottom-gap-font-px} (now the same for every alignment, default 0).
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

    /** Parses {@code #AARRGGBB} (or {@code #RRGGBB}, treated as opaque) into an ARGB int. */
    static int parseArgb(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        String hex = value.trim().replace("#", "");
        long parsed = Long.parseLong(hex, 16);
        return hex.length() <= 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
    }
}
