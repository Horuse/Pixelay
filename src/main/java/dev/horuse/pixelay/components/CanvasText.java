package dev.horuse.pixelay.components;

import dev.horuse.pixelay.content.TextContent;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * A text label positioned at a pixel coordinate and sized in pixels. Drawn with real entities seen by
 * everyone by default, for one player with {@link #perPlayer}, or packet-only with {@link #fake}.
 */
public class CanvasText extends AbstractCanvasComponent {

    private volatile TextContent content;

    /** A label drawn with real entities seen by everyone. */
    public CanvasText(double pixelX, double pixelY, double heightPx, TextContent content) {
        this(pixelX, pixelY, heightPx, content, drawable -> new EntityRenderer(drawable, null));
    }

    /** @param renderer creates the renderer for this element */
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

    /** A label drawn with real entities that only {@code viewer} sees. */
    public static CanvasText perPlayer(
        Player viewer,
        double pixelX,
        double pixelY,
        double heightPx,
        TextContent content
    ) {
        return new CanvasText(pixelX, pixelY, heightPx, content, drawable -> new EntityRenderer(drawable, viewer));
    }

    /** A label made of packets only, for {@code viewer}: nothing is spawned on the server. */
    public static CanvasText fake(Player viewer, double pixelX, double pixelY, double heightPx, TextContent content) {
        return new CanvasText(pixelX, pixelY, heightPx, content, drawable -> new FakeRenderer(viewer, drawable));
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
        return override != null ? override.render() : content.render();
    }

    /** Parses component text as MiniMessage — the single markup format used by canvas content. */
    static Component parse(String text) {
        return MiniMessage.miniMessage().deserialize(text);
    }
}
