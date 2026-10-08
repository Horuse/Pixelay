package dev.horuse.pixelay.components;

import dev.horuse.pixelay.content.TextContent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * A text label positioned at a pixel coordinate and sized in pixels. Spawned as ordinary server
 * entities by default, or per-player via {@link #packet(Player, double, double, double,
 * TextContent)}.
 */
public class CanvasText extends AbstractCanvasComponent {

    private final TextContent content;

    public CanvasText(double pixelX, double pixelY, double heightPx, TextContent content) {
        super(pixelX, pixelY, heightPx);
        this.content = content;
        this.renderer = new ServerCanvasRenderer(this);
    }

    /** Per-player variant: the label is rendered only for the given viewer via packets. */
    public static CanvasText packet(Player viewer, double pixelX, double pixelY, double heightPx, TextContent content) {
        CanvasText text = new CanvasText(pixelX, pixelY, heightPx, content);
        text.renderer = new PacketCanvasRenderer(viewer, text);
        return text;
    }

    /**
     * Fully fake variant: no server entity is spawned; spawn/metadata packets are sent only to the
     * given viewer.
     */
    public static CanvasText fake(Player viewer, double pixelX, double pixelY, double heightPx, TextContent content) {
        CanvasText text = new CanvasText(pixelX, pixelY, heightPx, content);
        text.renderer = new FakePacketCanvasRenderer(viewer, text);
        return text;
    }

    @Override
    public String renderText() {
        return content.render();
    }

    /** Parses component text as MiniMessage — the single markup format used by canvas content. */
    protected static Component parse(String text) {
        return MiniMessage.miniMessage().deserialize(text);
    }
}
