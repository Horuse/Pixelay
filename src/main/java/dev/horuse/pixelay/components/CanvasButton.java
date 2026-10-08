package dev.horuse.pixelay.components;

import dev.horuse.pixelay.content.TextContent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * A clickable button: text plus an automatically sized {@link org.bukkit.entity.Interaction} hitbox
 * spawned by its renderer. The hitbox is registered in {@link #CLICK_REGISTRY} so the shared {@link
 * dev.horuse.pixelay.listeners.CanvasClickListener} can route clicks. Spawned as ordinary server
 * entities by default, or per-player via {@link #packet}.
 */
public class CanvasButton extends CanvasText {

    private static final Map<UUID, Consumer<Player>> CLICK_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<Integer, ViewerClick> CLICK_REGISTRY_BY_ID = new ConcurrentHashMap<>();

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
     * Fully fake variant: no server entity is spawned; spawn/metadata packets (text + clickable
     * Interaction) are sent only to the given viewer, and clicks are routed by packet.
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

    public static Consumer<Player> getCallback(UUID uuid) {
        return CLICK_REGISTRY.get(uuid);
    }

    public static void registerClick(UUID uuid, Consumer<Player> callback) {
        CLICK_REGISTRY.put(uuid, callback);
    }

    public static void unregisterClick(UUID uuid) {
        CLICK_REGISTRY.remove(uuid);
    }

    /**
     * @return the callback of a per-player hitbox, or null when the entity is unknown or the clicking
     *     player is not its viewer — a modified client can send any entity id, so clicks on another
     *     player's private button must be ignored
     */
    public static Consumer<Player> getCallbackById(int entityId, Player clicker) {
        ViewerClick click = CLICK_REGISTRY_BY_ID.get(entityId);

        if (click == null || !click.viewer().equals(clicker.getUniqueId())) {
            return null;
        }

        return click.callback();
    }

    public static void registerClickById(int entityId, UUID viewer, Consumer<Player> callback) {
        CLICK_REGISTRY_BY_ID.put(entityId, new ViewerClick(viewer, callback));
    }

    public static void unregisterClickById(int entityId) {
        CLICK_REGISTRY_BY_ID.remove(entityId);
    }

    public static void clearRegistry() {
        CLICK_REGISTRY.clear();
        CLICK_REGISTRY_BY_ID.clear();
    }

    /** A per-player hitbox callback bound to the only player allowed to trigger it. */
    private record ViewerClick(UUID viewer, Consumer<Player> callback) {}
}
