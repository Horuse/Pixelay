package dev.horuse.pixelay.screen;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.layout.Easing;
import dev.horuse.pixelay.pack.PixelayPack;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Full-screen Core Shader Transitions rendered on the 2D GUI layer directly above the player's hand,
 * hotbar, hearts, and world geometry, at 144+ FPS native monitor refresh rate.
 */
final class ShaderTransition {

    enum Mode {
        IN,
        OUT,
    }

    private static final Key FONT_KEY = Key.key("pixelay", "transition");
    private static final String GLYPH = "\uE000";
    private static final int SIGNATURE_RED = 126; // 0x7E

    private final Player player;
    private final ScreenTransition transition;
    private final Mode mode;
    private BukkitTask task;

    ShaderTransition(Player player, ScreenTransition transition, Mode mode) {
        this.player = player;
        this.transition = transition;
        this.mode = mode;
    }

    /** Whether shader transitions are available (requires Pixelay resource pack). */
    static boolean available() {
        return PixelayPack.current() != null;
    }

    /** Plays the shader transition and runs {@code onFinished} once 100% complete. */
    void play(Runnable onFinished) {
        cancel();
        if (!player.isOnline()) {
            return;
        }

        int durationTicks = (int) Math.max(1, Math.round(transition.durationMs() / 50.0));
        int typeCode = typeCode(transition.type());
        int easingCode = easingCode(transition.easing());
        int colorIdx = colorIndex(transition.overlayColor());

        int modeBit = mode == Mode.OUT ? 1 : 0;
        int green = (typeCode & 15) | (modeBit << 4) | ((easingCode & 7) << 5);
        int blue = colorIdx & 0xFF;

        TextColor color = TextColor.color(SIGNATURE_RED, green, blue);
        Component titleComponent = Component.text(GLYPH).font(FONT_KEY).color(color);

        // Mode.IN: fadeIn=durationTicks, stay=40, fadeOut=0. vertexColor.a animates 0.0 -> 1.0, then stays 1.0.
        // Mode.OUT: fadeIn=0, stay=0, fadeOut=durationTicks. vertexColor.a animates 1.0 -> 0.0.
        // Starting Mode.OUT at alpha 1.0 ensures Minecraft never drops frame 0 due to "if (alpha <= 8) return;".
        Title.Times times =
            mode == Mode.IN
                ? Title.Times.times(Ticks.duration(durationTicks), Ticks.duration(40), Ticks.duration(0))
                : Title.Times.times(Ticks.duration(0), Ticks.duration(0), Ticks.duration(durationTicks));

        player.showTitle(Title.title(titleComponent, Component.empty(), times));

        task = Bukkit.getScheduler().runTaskLater(
            Pixelay.plugin(),
            () -> {
                task = null;
                if (player.isOnline() && onFinished != null) {
                    onFinished.run();
                }
            },
            durationTicks
        );
    }

    void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    static void clear(Player player) {
        if (player.isOnline()) {
            player.clearTitle();
        }
    }

    private static int typeCode(ScreenTransition.Type type) {
        return switch (type) {
            case FADE -> 0;
            case LINEAR_WIPE_RIGHT -> 1;
            case LINEAR_WIPE_LEFT -> 2;
            case LINEAR_WIPE_DOWN -> 3;
            case LINEAR_WIPE_UP -> 4;
            case CORNER_WIPE_TOP_LEFT -> 5;
            case CORNER_WIPE_TOP_RIGHT -> 6;
            case CORNER_WIPE_BOTTOM_LEFT -> 7;
            case CORNER_WIPE_BOTTOM_RIGHT -> 8;
            case CURTAIN -> 9;
            default -> 0;
        };
    }

    private static int easingCode(Easing easing) {
        if (easing == Easing.EASE_IN_OUT) return 1;
        if (easing == Easing.EASE_IN) return 2;
        if (easing == Easing.EASE_OUT) return 3;
        if (easing == Easing.SMOOTHSTEP) return 4;
        return 0; // LINEAR
    }

    private static int colorIndex(int argb) {
        int rgb = argb & 0x00FFFFFF;
        if (rgb == 0x1E1B4B) return 1;
        if (rgb == 0x0F172A) return 2;
        if (rgb == 0xFFFFFF) return 3;
        if (rgb == 0x18181B) return 4;
        if (rgb == 0x881337) return 5;
        return 0; // 0x000000 (Black)
    }
}
