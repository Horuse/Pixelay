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
 * Catches the clicks of a spectator player looking at a full-screen canvas, without any entity in front of
 * the camera: a spectator's click arrives as Spectate / interact / attack / swing packets (see
 * {@code CanvasPacketClickListener}), which are routed to {@code onClick} while the catcher is spawned.
 * The client moves its camera on such a click; {@code onCameraMoved} puts it back.
 */
public class CanvasClickCatcher implements CanvasComponent {

    private static final Map<UUID, CanvasClickCatcher> BY_VIEWER = new ConcurrentHashMap<>();
    private static final ClickDebounce DEBOUNCE = new ClickDebounce();
    /** Viewers holding Shift: their clicks are attempts to leave the camera, not clicks. */
    private static final Set<UUID> SNEAKING = ConcurrentHashMap.newKeySet();

    private final Player viewer;
    private final Consumer<Player> onClick;
    private final Runnable onCameraMoved;
    private boolean active;

    /** @param onCameraMoved puts the camera back after a click, or {@code null} */
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

    /** Puts the viewer's camera back after a click moved it. */
    public static void resetCamera(Player player) {
        CanvasClickCatcher catcher = player != null ? BY_VIEWER.get(player.getUniqueId()) : null;
        if (catcher != null && catcher.onCameraMoved != null) {
            catcher.onCameraMoved.run();
        }
    }

    /**
     * @return true if the player's swing/digging is routed to their screen
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
     * @return true if the player is viewing the screen with this click catcher
     */
    public static boolean isViewer(Player player) {
        return player != null && BY_VIEWER.containsKey(player.getUniqueId());
    }

    /** Reported by the packet listener from the player's input. */
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

    /** Forgets a player who left. */
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
