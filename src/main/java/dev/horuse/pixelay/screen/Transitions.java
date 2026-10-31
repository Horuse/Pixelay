package dev.horuse.pixelay.screen;

import dev.horuse.pixelay.Pixelay;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Plays full-screen transitions on players outside canvas sessions.
 * Supports shader transitions and packet display entity transitions.
 */
public final class Transitions {

    private static final long REVEAL_HOLD_TICKS = 2;
    private static final int COVER_HOLD_TICKS = 72000;
    private static final Map<UUID, ActiveTransition> ACTIVE = new ConcurrentHashMap<>();

    public Transitions() {}

    /** Plays a two-phase transition that covers and reveals the screen. */
    public static void play(
        Player player,
        ScreenTransition in,
        ScreenTransition out,
        Runnable onCovered,
        Runnable onFinished
    ) {
        Objects.requireNonNull(player, "player");
        if (!player.isOnline()) {
            return;
        }

        ScreenTransition inTrans = in != null ? in : ScreenTransition.none();
        ScreenTransition outTrans = out != null ? out : inTrans;

        if (inTrans.type() == ScreenTransition.Type.NONE || inTrans.durationMs() <= 0) {
            clear(player);
            if (onCovered != null) {
                onCovered.run();
            }
            if (outTrans.type() != ScreenTransition.Type.NONE && outTrans.durationMs() > 0) {
                reveal(player, outTrans, onFinished);
            } else if (onFinished != null) {
                onFinished.run();
            }
            return;
        }

        cover(player, inTrans, () -> {
            ActiveTransition active = ACTIVE.get(player.getUniqueId());
            if (active == null) {
                return;
            }

            if (onCovered != null) {
                onCovered.run();
            }

            if (!player.isOnline()) {
                clear(player);
                return;
            }

            active.holdTask = Bukkit.getScheduler().runTaskLater(
                Pixelay.plugin(),
                () -> {
                    if (ACTIVE.get(player.getUniqueId()) == active && player.isOnline()) {
                        reveal(player, outTrans, onFinished);
                    }
                },
                REVEAL_HOLD_TICKS
            );
        });
    }

    /** Plays a two-phase transition with a midpoint action and completion callback. */
    public static void play(Player player, ScreenTransition transition, Runnable onCovered, Runnable onFinished) {
        play(player, transition, transition, onCovered, onFinished);
    }

    /** Plays a two-phase transition and executes an action when covered. */
    public static void play(Player player, ScreenTransition transition, Runnable onCovered) {
        play(player, transition, transition, onCovered, null);
    }

    /** Plays a two-phase transition without midpoint actions. */
    public static void play(Player player, ScreenTransition transition) {
        play(player, transition, transition, null, null);
    }

    /** Animates screen coverage and executes a callback when complete. */
    public static void cover(Player player, ScreenTransition transition, Runnable onCovered) {
        Objects.requireNonNull(player, "player");
        if (!player.isOnline()) {
            return;
        }

        if (transition == null || transition.type() == ScreenTransition.Type.NONE || transition.durationMs() <= 0) {
            clear(player);
            if (onCovered != null) {
                onCovered.run();
            }
            return;
        }

        clear(player);
        ActiveTransition active = new ActiveTransition(player);
        ACTIVE.put(player.getUniqueId(), active);

        if (ShaderTransition.available()) {
            ShaderTransition shader = new ShaderTransition(
                player,
                transition,
                ShaderTransition.Mode.IN,
                COVER_HOLD_TICKS
            );
            active.shader = shader;
            shader.play(() -> {
                if (ACTIVE.get(player.getUniqueId()) != active) {
                    return;
                }
                if (onCovered != null && player.isOnline()) {
                    onCovered.run();
                }
            });
            return;
        }

        Location eye = player.getEyeLocation();
        ScreenFader fader = ScreenFader.createWorldFader(player, eye, transition, 0);
        active.fader = fader;
        fader.animate(0, 1, () -> {
            if (ACTIVE.get(player.getUniqueId()) != active) {
                fader.remove();
                return;
            }
            if (onCovered != null && player.isOnline()) {
                onCovered.run();
            }
        });
    }

    /** Animates screen coverage to a full overlay. */
    public static void cover(Player player, ScreenTransition transition) {
        cover(player, transition, null);
    }

    /** Animates screen reveal and executes a callback when complete. */
    public static void reveal(Player player, ScreenTransition transition, Runnable onRevealed) {
        Objects.requireNonNull(player, "player");
        if (!player.isOnline()) {
            return;
        }

        if (transition == null || transition.type() == ScreenTransition.Type.NONE || transition.durationMs() <= 0) {
            clear(player);
            if (onRevealed != null) {
                onRevealed.run();
            }
            return;
        }

        ActiveTransition previous = ACTIVE.remove(player.getUniqueId());
        if (previous != null) {
            if (previous.holdTask != null) {
                previous.holdTask.cancel();
            }
            if (previous.shader != null) {
                previous.shader.cancel();
            }
            if (previous.fader != null) {
                previous.fader.remove();
            }
        }

        ActiveTransition active = new ActiveTransition(player);
        ACTIVE.put(player.getUniqueId(), active);

        if (ShaderTransition.available()) {
            ShaderTransition shader = new ShaderTransition(player, transition, ShaderTransition.Mode.OUT);
            active.shader = shader;
            shader.play(() -> {
                ACTIVE.remove(player.getUniqueId(), active);
                ShaderTransition.clear(player);
                if (onRevealed != null && player.isOnline()) {
                    onRevealed.run();
                }
            });
            return;
        }

        Location eye = player.getEyeLocation();
        ScreenFader fader = ScreenFader.createWorldFader(player, eye, transition, 1);
        active.fader = fader;
        fader.animate(1, 2, () -> {
            ACTIVE.remove(player.getUniqueId(), active);
            fader.remove();
            if (onRevealed != null && player.isOnline()) {
                onRevealed.run();
            }
        });
    }

    /** Animates screen reveal from a full overlay. */
    public static void reveal(Player player, ScreenTransition transition) {
        reveal(player, transition, null);
    }

    /** Reveals the world from black over the specified duration. */
    public static void fadeIn(Player player, long durationMs) {
        reveal(player, ScreenTransition.fade(durationMs), null);
    }

    /** Reveals the world from black and executes a callback when complete. */
    public static void fadeIn(Player player, long durationMs, Runnable onFinished) {
        reveal(player, ScreenTransition.fade(durationMs), onFinished);
    }

    /** Covers the world to black over the specified duration. */
    public static void fadeOut(Player player, long durationMs) {
        cover(player, ScreenTransition.fade(durationMs), null);
    }

    /** Covers the world to black and executes a callback when complete. */
    public static void fadeOut(Player player, long durationMs, Runnable onCovered) {
        cover(player, ScreenTransition.fade(durationMs), onCovered);
    }

    /** Clears any active transition on the player. */
    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        ActiveTransition active = ACTIVE.remove(player.getUniqueId());
        if (active != null) {
            active.cancel();
        }
    }

    /** Clears all active transitions on all players. */
    public static void clearAll() {
        for (ActiveTransition active : ACTIVE.values()) {
            active.cancel();
        }
        ACTIVE.clear();
    }

    /** Checks if a transition is active for the player. */
    public static boolean isTransitioning(Player player) {
        return player != null && ACTIVE.containsKey(player.getUniqueId());
    }

    // --- Instance delegate methods for Pixelay.transitions() ---

    public void playTransition(
        Player player,
        ScreenTransition in,
        ScreenTransition out,
        Runnable onCovered,
        Runnable onFinished
    ) {
        play(player, in, out, onCovered, onFinished);
    }

    public void playTransition(Player player, ScreenTransition transition, Runnable onCovered, Runnable onFinished) {
        play(player, transition, onCovered, onFinished);
    }

    public void playTransition(Player player, ScreenTransition transition, Runnable onCovered) {
        play(player, transition, onCovered);
    }

    public void playTransition(Player player, ScreenTransition transition) {
        play(player, transition);
    }

    public void coverScreen(Player player, ScreenTransition transition, Runnable onCovered) {
        cover(player, transition, onCovered);
    }

    public void coverScreen(Player player, ScreenTransition transition) {
        cover(player, transition);
    }

    public void revealScreen(Player player, ScreenTransition transition, Runnable onRevealed) {
        reveal(player, transition, onRevealed);
    }

    public void revealScreen(Player player, ScreenTransition transition) {
        reveal(player, transition);
    }

    private static final class ActiveTransition {

        final Player player;
        BukkitTask holdTask;
        ShaderTransition shader;
        ScreenFader fader;

        ActiveTransition(Player player) {
            this.player = player;
        }

        void cancel() {
            if (holdTask != null) {
                holdTask.cancel();
                holdTask = null;
            }
            if (shader != null) {
                shader.cancel();
                shader = null;
            }
            if (fader != null) {
                fader.remove();
                fader = null;
            }
            if (player.isOnline()) {
                ShaderTransition.clear(player);
            }
        }
    }
}
