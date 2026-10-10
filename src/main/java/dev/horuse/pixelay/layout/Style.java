package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.content.TextContent;

/**
 * What changes on a box in a state (see {@link Box#state}), like the body of a CSS rule such as
 * {@code :hover { ... }}. Unset properties keep their normal value.
 */
public final class Style {

    private Integer background;
    private TextContent text;

    Style() {}

    /** Background colour (ARGB). */
    public Style background(int argb) {
        this.background = argb;
        return this;
    }

    /** Text of the component (for {@code node(component)}), MiniMessage. */
    public Style text(String miniMessage) {
        return text(TextContent.of(miniMessage));
    }

    /** Text of the component (for {@code node(component)}). */
    public Style text(TextContent content) {
        this.text = content;
        return this;
    }

    Integer background() {
        return background;
    }

    TextContent text() {
        return text;
    }

    /** Fills what is still unset here from {@code other} (a less important state). */
    void fillFrom(Style other) {
        if (background == null) {
            background = other.background;
        }
        if (text == null) {
            text = other.text;
        }
    }
}
