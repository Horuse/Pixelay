package dev.horuse.pixelay.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;

/**
 * Manages an on-screen HUD for a player using a transparent Adventure BossBar.
 * Combines multiple {@link HudElement} instances into an on-screen composite layer.
 */
public final class ScreenHud {

    private final Player player;
    private final BossBar bossBar;
    private final List<HudElement> elements = new ArrayList<>();
    private boolean visible = false;

    /**
     * Creates a screen HUD controller for the specified player.
     *
     * @param player the player target
     */
    public ScreenHud(Player player) {
        this.player = Objects.requireNonNull(player, "player");
        this.bossBar = BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
    }

    /**
     * Returns the target player.
     *
     * @return the player
     */
    public Player player() {
        return player;
    }

    /**
     * Adds an element to this HUD.
     *
     * @param element the HUD element
     * @return this instance
     */
    public ScreenHud add(HudElement element) {
        if (element != null && !elements.contains(element)) {
            elements.add(element);
        }
        return this;
    }

    /**
     * Removes an element from this HUD.
     *
     * @param element the HUD element
     * @return this instance
     */
    public ScreenHud remove(HudElement element) {
        elements.remove(element);
        return this;
    }

    /** Clears all elements from this HUD. */
    public void clear() {
        elements.clear();
    }

    /**
     * Returns an unmodifiable list of active elements.
     *
     * @return the list of elements
     */
    public List<HudElement> elements() {
        return Collections.unmodifiableList(elements);
    }

    /** Shows the HUD on the player screen. */
    public void show() {
        if (!visible && player.isOnline()) {
            player.showBossBar(bossBar);
            visible = true;
            update();
        }
    }

    /** Hides the HUD from the player screen. */
    public void hide() {
        if (visible) {
            player.hideBossBar(bossBar);
            visible = false;
        }
    }

    /**
     * Returns whether the HUD is currently visible.
     *
     * @return true if visible
     */
    public boolean isVisible() {
        return visible;
    }

    /** Re-renders and sends the composite HUD layer to the player. */
    public void update() {
        if (!visible || !player.isOnline()) {
            return;
        }
        if (elements.isEmpty()) {
            bossBar.name(Component.empty());
            return;
        }
        TextComponent.Builder root = Component.text();
        for (HudElement element : elements) {
            Component rendered = element.render();
            if (rendered != null) {
                root.append(rendered);
            }
        }
        bossBar.name(root.build());
    }
}
