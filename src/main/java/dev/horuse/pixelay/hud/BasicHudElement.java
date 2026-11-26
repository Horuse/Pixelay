package dev.horuse.pixelay.hud;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/**
 * Basic implementation of a 2D screen HUD element.
 */
public class BasicHudElement implements HudElement {

    private final HudAnchor anchor;
    private final int offsetX;
    private final int offsetY;
    private Component content;

    /**
     * Creates a basic HUD element.
     *
     * @param anchor the screen anchor
     * @param offsetX horizontal pixel offset
     * @param offsetY vertical pixel offset
     * @param content content component
     */
    public BasicHudElement(HudAnchor anchor, int offsetX, int offsetY, Component content) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.content = content != null ? content : Component.empty();
    }

    @Override
    public HudAnchor anchor() {
        return anchor;
    }

    @Override
    public int offsetX() {
        return offsetX;
    }

    @Override
    public int offsetY() {
        return offsetY;
    }

    /**
     * Sets new content for this element.
     *
     * @param content the updated component
     */
    public void content(Component content) {
        this.content = content != null ? content : Component.empty();
    }

    /**
     * Returns the current content.
     *
     * @return the content component
     */
    public Component content() {
        return content;
    }

    @Override
    public Component render() {
        if (offsetX == 0 || Component.empty().equals(content)) {
            return content;
        }
        return Component.text()
            .append(HudSpace.offset(offsetX))
            .append(content)
            .append(HudSpace.offset(-offsetX))
            .build();
    }
}
