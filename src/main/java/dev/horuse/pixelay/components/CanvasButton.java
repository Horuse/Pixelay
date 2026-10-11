package dev.horuse.pixelay.components;

import dev.horuse.pixelay.content.TextContent;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.entity.Player;

/**
 * A clickable button: text whose exact box is clickable. Clicks reach it through {@link
 * dev.horuse.pixelay.clicks.CanvasClicks} (a click on the canvas, a screen cursor or your own code),
 * whatever the canvas rotation. Drawn with real entities seen by everyone by default, for one player with
 * {@link #perPlayer}, or packet-only with {@link #fake}.
 */
public class CanvasButton extends CanvasText {

    private final Consumer<Player> onClick;

    /** A button drawn with real entities seen (and clicked) by everyone. */
    public CanvasButton(double pixelX, double pixelY, double heightPx, TextContent content, Consumer<Player> onClick) {
        this(pixelX, pixelY, heightPx, content, onClick, drawable -> new EntityRenderer(drawable, null));
    }

    private CanvasButton(
        double pixelX,
        double pixelY,
        double heightPx,
        TextContent content,
        Consumer<Player> onClick,
        Function<CanvasDrawable, CanvasRenderer> renderer
    ) {
        super(pixelX, pixelY, heightPx, content, renderer);
        this.onClick = onClick;
    }

    /** A button drawn with real entities that only {@code viewer} sees and clicks. */
    public static CanvasButton perPlayer(
        Player viewer,
        double pixelX,
        double pixelY,
        double heightPx,
        TextContent content,
        Consumer<Player> onClick
    ) {
        return new CanvasButton(pixelX, pixelY, heightPx, content, onClick, d -> new EntityRenderer(d, viewer));
    }

    /** A button made of packets only, for {@code viewer}: nothing is spawned on the server. */
    public static CanvasButton fake(
        Player viewer,
        double pixelX,
        double pixelY,
        double heightPx,
        TextContent content,
        Consumer<Player> onClick
    ) {
        return new CanvasButton(pixelX, pixelY, heightPx, content, onClick, d -> new FakeRenderer(viewer, d));
    }

    @Override
    public Consumer<Player> onClick() {
        return onClick;
    }
}
