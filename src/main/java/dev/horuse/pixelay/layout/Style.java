package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.content.TextContent;
import dev.horuse.pixelay.utils.ColorUtils;

/**
 * What changes on a box in a state (see {@link Box#state}), like the body of a CSS rule such as
 * {@code :hover { ... }}. Unset properties keep their normal value.
 */
public final class Style {

    private Integer background;
    private Integer borderColor;
    private Insets borderWidths;
    private Double opacity;
    private TextContent text;
    private Insets padding;
    private Insets margin;
    private String cursor;

    Style() {}

    /** Background colour (ARGB). */
    public Style background(int argb) {
        this.background = argb;
        return this;
    }

    /** Background colour from CSS hex/rgb/rgba string (e.g. {@code "#0B1426"} or {@code "rgba(11, 20, 38, 0.8)"}). */
    public Style background(String cssColor) {
        return background(ColorUtils.parse(cssColor));
    }

    /** Border width and colour on all four sides while in this state. */
    public Style border(double widthPx, int argb) {
        this.borderWidths = Insets.all(widthPx);
        this.borderColor = argb;
        return this;
    }

    /** Border width and CSS colour on all four sides while in this state. */
    public Style border(double widthPx, String cssColor) {
        return border(widthPx, ColorUtils.parse(cssColor));
    }

    /** Border colour (ARGB) while in this state. */
    public Style borderColor(int argb) {
        this.borderColor = argb;
        return this;
    }

    /** Border colour from CSS string while in this state. */
    public Style borderColor(String cssColor) {
        return borderColor(ColorUtils.parse(cssColor));
    }

    /** Opacity multiplier ({@code 0.0..1.0}) while in this state. */
    public Style opacity(double opacity) {
        this.opacity = Math.clamp(opacity, 0.0, 1.0);
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

    /** Padding while in the state (the layout is redone). */
    public Style padding(double all) {
        return padding(Insets.all(all));
    }

    public Style padding(Insets padding) {
        this.padding = padding;
        return this;
    }

    /** Margin while in the state (the layout is redone). */
    public Style margin(Insets margin) {
        this.margin = margin;
        return this;
    }

    /** CSS {@code cursor} while in the state, e.g. {@code "not-allowed"} for {@code State.DISABLED}. */
    public Style cursor(String name) {
        this.cursor = name;
        return this;
    }

    public String cursor() {
        return cursor;
    }

    public Insets padding() {
        return padding;
    }

    public Insets margin() {
        return margin;
    }

    public Integer background() {
        return background;
    }

    public Integer borderColor() {
        return borderColor;
    }

    public Insets borderWidths() {
        return borderWidths;
    }

    public Double opacity() {
        return opacity;
    }

    public TextContent text() {
        return text;
    }

    /** Fills what is still unset here from {@code other} (a less important state). */
    void fillFrom(Style other) {
        if (background == null) {
            background = other.background;
        }
        if (borderColor == null) {
            borderColor = other.borderColor;
        }
        if (borderWidths == null) {
            borderWidths = other.borderWidths;
        }
        if (opacity == null) {
            opacity = other.opacity;
        }
        if (text == null) {
            text = other.text;
        }
        if (padding == null) {
            padding = other.padding;
        }
        if (margin == null) {
            margin = other.margin;
        }
        if (cursor == null) {
            cursor = other.cursor;
        }
    }
}
