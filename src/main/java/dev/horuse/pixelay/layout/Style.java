package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.content.TextContent;
import dev.horuse.pixelay.utils.ColorUtils;

/**
 * Defines styling overrides applied when an interactive state is active.
 * Properties not specified retain their base values.
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
    private Double translateX;
    private Double translateY;
    private Double translatePxX;
    private Double translatePxY;
    private Double scaleX;
    private Double scaleY;
    private Double rotateDeg;
    private Double rotateXDeg;
    private Double rotateYDeg;
    private Double originX;
    private Double originY;

    Style() {}

    /** Sets background ARGB color for this state. */
    public Style background(int argb) {
        this.background = argb;
        return this;
    }

    /** Sets background color from a CSS color string. */
    public Style background(String cssColor) {
        return background(ColorUtils.parse(cssColor));
    }

    /** Sets border width and ARGB color on all sides. */
    public Style border(double widthPx, int argb) {
        this.borderWidths = Insets.all(widthPx);
        this.borderColor = argb;
        return this;
    }

    /** Sets border width and CSS color string on all sides. */
    public Style border(double widthPx, String cssColor) {
        return border(widthPx, ColorUtils.parse(cssColor));
    }

    /** Sets border ARGB color. */
    public Style borderColor(int argb) {
        this.borderColor = argb;
        return this;
    }

    /** Sets border color from a CSS color string. */
    public Style borderColor(String cssColor) {
        return borderColor(ColorUtils.parse(cssColor));
    }

    /** Sets opacity multiplier between 0.0 and 1.0. */
    public Style opacity(double opacity) {
        this.opacity = Math.clamp(opacity, 0.0, 1.0);
        return this;
    }

    /** Sets text content from a MiniMessage string. */
    public Style text(String miniMessage) {
        return text(TextContent.of(miniMessage));
    }

    /** Sets text content. */
    public Style text(TextContent content) {
        this.text = content;
        return this;
    }

    /** Sets padding insets for this state. */
    public Style padding(double all) {
        return padding(Insets.all(all));
    }

    public Style padding(Insets padding) {
        this.padding = padding;
        return this;
    }

    /** Sets margin insets for this state. */
    public Style margin(Insets margin) {
        this.margin = margin;
        return this;
    }

    /** Sets cursor sprite name for this state. */
    public Style cursor(String name) {
        this.cursor = name;
        return this;
    }

    /** Sets percentage translation for this state. */
    public Style translate(double percentX, double percentY) {
        this.translateX = percentX;
        this.translateY = percentY;
        return this;
    }

    /** Sets pixel translation for this state. */
    public Style translatePx(double dxPx, double dyPx) {
        this.translatePxX = dxPx;
        this.translatePxY = dyPx;
        return this;
    }

    public Style translateX(double dxPx) {
        this.translatePxX = dxPx;
        return this;
    }

    public Style translateY(double dyPx) {
        this.translatePxY = dyPx;
        return this;
    }

    /** Sets uniform scale factor for this state. */
    public Style scale(double factor) {
        return scale(factor, factor);
    }

    /** Sets scale factors for this state. */
    public Style scale(double sx, double sy) {
        this.scaleX = sx;
        this.scaleY = sy;
        return this;
    }

    public Style scaleX(double sx) {
        this.scaleX = sx;
        return this;
    }

    public Style scaleY(double sy) {
        this.scaleY = sy;
        return this;
    }

    /** Sets clockwise 2D rotation in degrees. */
    public Style rotate(double degrees) {
        this.rotateDeg = degrees;
        return this;
    }

    /** Sets 3D pitch rotation in degrees. */
    public Style rotateX(double degrees) {
        this.rotateXDeg = degrees;
        return this;
    }

    /** Sets 3D yaw rotation in degrees. */
    public Style rotateY(double degrees) {
        this.rotateYDeg = degrees;
        return this;
    }

    /** Sets transform origin ratios from 0.0 to 1.0. */
    public Style transformOrigin(double ox, double oy) {
        this.originX = ox;
        this.originY = oy;
        return this;
    }

    /** Sets transform configuration for this state. */
    public Style transform(Transform t) {
        if (t == null) {
            return this;
        }
        this.translateX = t.translateX();
        this.translateY = t.translateY();
        this.translatePxX = t.translatePxX();
        this.translatePxY = t.translatePxY();
        this.scaleX = t.scaleX();
        this.scaleY = t.scaleY();
        this.rotateDeg = t.rotateDeg();
        this.rotateXDeg = t.rotateXDeg();
        this.rotateYDeg = t.rotateYDeg();
        this.originX = t.originX();
        this.originY = t.originY();
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

    public Double translateX() {
        return translateX;
    }

    public Double translateY() {
        return translateY;
    }

    public Double translatePxX() {
        return translatePxX;
    }

    public Double translatePxY() {
        return translatePxY;
    }

    public Double scaleX() {
        return scaleX;
    }

    public Double scaleY() {
        return scaleY;
    }

    public Double rotateDeg() {
        return rotateDeg;
    }

    public Double rotateXDeg() {
        return rotateXDeg;
    }

    public Double rotateYDeg() {
        return rotateYDeg;
    }

    public Double originX() {
        return originX;
    }

    public Double originY() {
        return originY;
    }

    boolean hasTransform() {
        return (
            translateX != null ||
            translateY != null ||
            translatePxX != null ||
            translatePxY != null ||
            scaleX != null ||
            scaleY != null ||
            rotateDeg != null ||
            rotateXDeg != null ||
            rotateYDeg != null ||
            originX != null ||
            originY != null
        );
    }

    /** Applies unset properties from another style. */
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
        if (translateX == null) {
            translateX = other.translateX;
        }
        if (translateY == null) {
            translateY = other.translateY;
        }
        if (translatePxX == null) {
            translatePxX = other.translatePxX;
        }
        if (translatePxY == null) {
            translatePxY = other.translatePxY;
        }
        if (scaleX == null) {
            scaleX = other.scaleX;
        }
        if (scaleY == null) {
            scaleY = other.scaleY;
        }
        if (rotateDeg == null) {
            rotateDeg = other.rotateDeg;
        }
        if (rotateXDeg == null) {
            rotateXDeg = other.rotateXDeg;
        }
        if (rotateYDeg == null) {
            rotateYDeg = other.rotateYDeg;
        }
        if (originX == null) {
            originX = other.originX;
        }
        if (originY == null) {
            originY = other.originY;
        }
    }
}
