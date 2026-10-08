package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.data.Canvas;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * Full-screen canvas click router: binds the player to a click handling callback. Does not create
 * fake entities in front of the player's eyes, and clicks in SPECTATOR come through Spectate
 * network packets (own UUID or UUID of the entity under the crosshair), which are intercepted and
 * used as a click signal.
 */
public class CanvasClickCatcher extends AbstractCanvasComponent {

    private static final Map<UUID, Consumer<Player>> BY_VIEWER = new ConcurrentHashMap<>();
    private static final Map<UUID, Runnable> CAMERA_RESET = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();

    private final Player viewer;
    private final Consumer<Player> onClick;
    private boolean active;

    public CanvasClickCatcher(Player viewer, Consumer<Player> onClick) {
        super(0, 0, 0);
        this.viewer = viewer;
        this.onClick = onClick;
    }

    @Override
    public void spawn(Canvas canvas) {
        this.canvas = canvas;
        active = true;
        BY_VIEWER.put(viewer.getUniqueId(), onClick);
    }

    @Override
    public void despawn() {
        active = false;
        BY_VIEWER.remove(viewer.getUniqueId());
        CAMERA_RESET.remove(viewer.getUniqueId());
        LAST_CLICK.remove(viewer.getUniqueId());
    }

    @Override
    public boolean isValid() {
        return active;
    }

    @Override
    public void refresh() {}

    @Override
    public String renderText() {
        return "";
    }

    @Override
    public Consumer<Player> onClick() {
        return onClick;
    }

    /** Registers the action of returning the camera to the anchor for the viewer. */
    public static void registerCameraReset(Player player, Runnable reset) {
        if (player != null && reset != null) {
            CAMERA_RESET.put(player.getUniqueId(), reset);
        }
    }

    /** Removes the camera reset action for the viewer. */
    public static void unregisterCameraReset(Player player) {
        if (player != null) {
            CAMERA_RESET.remove(player.getUniqueId());
        }
    }

    /** Returns the viewer's camera to the anchor. */
    public static void resetCamera(Player player) {
        if (player == null) {
            return;
        }

        Runnable reset = CAMERA_RESET.get(player.getUniqueId());

        if (reset != null) {
            reset.run();
        }
    }

    /**
     * @return true if the player's swing/digging is routed to their screen
     */
    public static boolean handleSwing(Player player) {
        if (player == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long last = LAST_CLICK.get(player.getUniqueId());

        if (last != null && now - last < Pixelay.settings().clicks().debounceMs()) {
            return true;
        }

        LAST_CLICK.put(player.getUniqueId(), now);

        Consumer<Player> callback = BY_VIEWER.get(player.getUniqueId());

        if (callback == null) {
            return false;
        }

        callback.accept(player);
        return true;
    }

    /**
     * @return true if the player is viewing the screen with this click catcher
     */
    public static boolean isViewer(Player player) {
        return player != null && BY_VIEWER.containsKey(player.getUniqueId());
    }

    public static void clearRegistry() {
        BY_VIEWER.clear();
        CAMERA_RESET.clear();
        LAST_CLICK.clear();
    }
}
