package dev.horuse.pixelay.clicks;

import dev.horuse.pixelay.components.CanvasComponent;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.utils.ClickDebounce;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * Catches clicks from a spectator looking at a full-screen canvas without entities before the camera.
 * A spectator click arrives as spectate, interact, attack, or swing packets.
 * Pixelay routes these packets to {@code onClick} while the catcher is active.
 * The client moves its camera on click. {@code onCameraMoved} restores the camera position.
 */
public class CanvasClickCatcher implements CanvasComponent {

    private static final Map<UUID, CanvasClickCatcher> BY_VIEWER = new ConcurrentHashMap<>();
    private static final ClickDebounce DEBOUNCE = new ClickDebounce();
    /** Viewers holding Shift. Their clicks indicate attempts to exit the camera, not canvas clicks. */
    private static final Set<UUID> SNEAKING = ConcurrentHashMap.newKeySet();

    private final Player viewer;
    private final Consumer<Player> onClick;
    private final Runnable onCameraMoved;
    private boolean active;

    /** @param onCameraMoved callback to restore the camera after a click, or {@code null} */
    public CanvasClickCatcher(Player viewer, Consumer<Player> onClick, Runnable onCameraMoved) {
        this.viewer = viewer;
        this.onClick = onClick;
        this.onCameraMoved = onCameraMoved;
    }

    @Override
    public void spawn(Canvas canvas) {
        active = true;
        BY_VIEWER.put(viewer.getUniqueId(), this);
    }

    @Override
    public void despawn() {
        active = false;
        BY_VIEWER.remove(viewer.getUniqueId(), this);
        DEBOUNCE.forget(viewer.getUniqueId());
        SNEAKING.remove(viewer.getUniqueId());
    }

    @Override
    public boolean isValid() {
        return active;
    }

    @Override
    public void refresh() {}

    /** Restores the camera position of the viewer after a click. */
    public static void resetCamera(Player player) {
        CanvasClickCatcher catcher = player != null ? BY_VIEWER.get(player.getUniqueId()) : null;
        if (catcher != null && catcher.onCameraMoved != null) {
            catcher.onCameraMoved.run();
        }
    }

    /**
     * @return true if Pixelay routes the player swing or digging packet to the screen
     */
    public static boolean handleSwing(Player player) {
        if (player == null) {
            return false;
        }
        if (!DEBOUNCE.accept(player.getUniqueId())) {
            return true;
        }
        CanvasClickCatcher catcher = BY_VIEWER.get(player.getUniqueId());
        if (catcher == null) {
            return false;
        }
        catcher.onClick.accept(player);
        return true;
    }

    /**
     * @return true if the player views the screen with this click catcher
     */
    public static boolean isViewer(Player player) {
        return player != null && BY_VIEWER.containsKey(player.getUniqueId());
    }

    /** Updates the sneaking state that the packet listener reports from player input. */
    public static void setSneaking(Player player, boolean sneaking) {
        if (sneaking) {
            SNEAKING.add(player.getUniqueId());
        } else {
            SNEAKING.remove(player.getUniqueId());
        }
    }

    public static boolean isSneaking(Player player) {
        return SNEAKING.contains(player.getUniqueId());
    }

    /** Removes data for a disconnected player. */
    public static void forget(UUID player) {
        SNEAKING.remove(player);
        DEBOUNCE.forget(player);
    }

    public static void clearRegistry() {
        BY_VIEWER.clear();
        SNEAKING.clear();
        DEBOUNCE.clear();
    }
}
