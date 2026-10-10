package dev.horuse.pixelay.components;

import dev.horuse.pixelay.content.TextContent;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * A clickable button: text whose exact box is clickable. Clicks reach it through {@link
 * dev.horuse.pixelay.clicks.CanvasClicks} (a click on the canvas, a screen cursor or your own code),
 * whatever the canvas rotation. Spawned as ordinary server entities by default, per-player via
 * {@link #packet}, or packet-only via {@link #fake}.
 */
public class CanvasButton extends CanvasText {

    private final Consumer<Player> onClick;

    public CanvasButton(double pixelX, double pixelY, double heightPx, TextContent content, Consumer<Player> onClick) {
        super(pixelX, pixelY, heightPx, content);
        this.onClick = onClick;
    }

    /** Per-player variant: button rendered only for the given viewer via packets. */
    public static CanvasButton packet(
        Player viewer,
        double pixelX,
        double pixelY,
        double heightPx,
        TextContent content,
        Consumer<Player> onClick
    ) {
        CanvasButton button = new CanvasButton(pixelX, pixelY, heightPx, content, onClick);
        button.renderer = new PacketCanvasRenderer(viewer, button);
        return button;
    }

    /**
     * Fully fake variant: no server entity is spawned; packets are sent only to the given viewer.
     */
    public static CanvasButton fake(
        Player viewer,
        double pixelX,
        double pixelY,
        double heightPx,
        TextContent content,
        Consumer<Player> onClick
    ) {
        CanvasButton button = new CanvasButton(pixelX, pixelY, heightPx, content, onClick);
        button.renderer = new FakePacketCanvasRenderer(viewer, button);
        return button;
    }

    @Override
    public Consumer<Player> onClick() {
        return onClick;
    }
}
