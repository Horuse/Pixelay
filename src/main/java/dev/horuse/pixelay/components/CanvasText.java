package dev.horuse.pixelay.components;

import dev.horuse.pixelay.content.TextContent;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Renders a text label at pixel coordinates with pixel height.
 * Supports public server entities, per-player visibility, and packet-only mode.
 */
public class CanvasText extends AbstractCanvasComponent {

    private volatile TextContent content;

    /** Creates a text label visible to all players. */
    public CanvasText(double pixelX, double pixelY, double heightPx, TextContent content) {
        this(pixelX, pixelY, heightPx, content, drawable -> new EntityRenderer(drawable, null));
    }

    /** @param renderer factory function that creates the renderer for this element */
    protected CanvasText(
        double pixelX,
        double pixelY,
        double heightPx,
        TextContent content,
        Function<CanvasDrawable, CanvasRenderer> renderer
    ) {
        super(pixelX, pixelY, heightPx);
        this.content = content;
        this.renderer = renderer.apply(this);
    }

    /** Creates a server entity text label visible only to {@code viewer}. */
    public static CanvasText perPlayer(
        Player viewer,
        double pixelX,
        double pixelY,
        double heightPx,
        TextContent content
    ) {
        return new CanvasText(pixelX, pixelY, heightPx, content, drawable -> new EntityRenderer(drawable, viewer));
    }

    /** Creates a packet-only text label for {@code viewer}. No server entity spawns. */
    public static CanvasText fake(Player viewer, double pixelX, double pixelY, double heightPx, TextContent content) {
        return new CanvasText(pixelX, pixelY, heightPx, content, drawable -> new FakeRenderer(viewer, drawable));
    }

    /** Binds a viewer and switches the label to packet-only rendering if unassigned. */
    public void bindViewer(Player viewer) {
        if (viewer != null && renderer.viewer() == null) {
            renderer = new FakeRenderer(viewer, this);
        }
    }

    @Override
    public void setContent(TextContent content) {
        if (content != null) {
            this.content = content;
        }
    }

    @Override
    public String renderText() {
        TextContent override = textOverride();
        String raw = override != null ? override.render() : content.render();
        int ellipsisLimit = ellipsisWidthFontPx();
        if (ellipsisLimit > 0) {
            return dev.horuse.pixelay.content.FontMetrics.truncateEllipsis(raw, ellipsisLimit);
        }
        return raw;
    }

    /** Deserializes string markup into a MiniMessage component. */
    static Component parse(String text) {
        return MiniMessage.miniMessage().deserialize(text);
    }
}
