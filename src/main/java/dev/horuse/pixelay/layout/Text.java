package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.components.CanvasText;
import dev.horuse.pixelay.components.LayoutComponent;
import dev.horuse.pixelay.components.TextPlacement;
import dev.horuse.pixelay.content.FontMetrics;
import dev.horuse.pixelay.content.TextContent;
import dev.horuse.pixelay.content.TextWrap;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import dev.horuse.pixelay.data.VAlign;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * Renders text within a layout following CSS text layout rules.
 * Wraps text at allocated boundaries without breaking words unless configured.
 */
public final class Text extends Box<Text> {

    private final AbstractCanvasComponent component;
    private HAlign textAlign;
    private TextOverflow textOverflow = TextOverflow.CLIP;
    // Layout pass cached string content and longest word width.
    private String text;
    private int longestWord = -1;

    public Text(AbstractCanvasComponent component) {
        this.component = component;
    }

    public Text(Player viewer, double heightPx, TextContent content) {
        this(
            viewer != null ? CanvasText.fake(viewer, 0, 0, heightPx, content) : new CanvasText(0, 0, heightPx, content)
        );
    }

    public Text(Player viewer, double heightPx, String miniMessage) {
        this(viewer, heightPx, TextContent.of(miniMessage));
    }

    public Text(double heightPx, TextContent content) {
        this(null, heightPx, content);
    }

    public Text(double heightPx, String miniMessage) {
        this(null, heightPx, TextContent.of(miniMessage));
    }

    @Override
    public String tagName() {
        return "text";
    }

    @Override
    public AbstractCanvasComponent component() {
        return component;
    }

    @Override
    void bindViewer(Player viewer) {
        if (component instanceof CanvasText canvasText) {
            canvasText.bindViewer(viewer);
        }
    }

    /** Sets horizontal text alignment within this box. */
    public Text textAlign(HAlign align) {
        this.textAlign = align;
        return this;
    }

    /** Sets horizontal text alignment using layout alignment. */
    public Text textAlign(Align align) {
        this.textAlign =
            align == null
                ? null
                : switch (align) {
                      case START, STRETCH -> HAlign.LEFT;
                      case CENTER -> HAlign.CENTER;
                      case END -> HAlign.RIGHT;
                  };
        return this;
    }

    public HAlign textAlign() {
        return textAlign;
    }

    /** Sets font size in canvas pixels. */
    public Text fontSize(double heightPx) {
        component.setHeightPx(heightPx);
        invalidate();
        return this;
    }

    public double fontSize() {
        return component.heightPx();
    }

    /** Updates text content. */
    public Text text(String miniMessage) {
        return text(TextContent.of(miniMessage));
    }

    /** Updates text content. */
    public Text text(TextContent content) {
        component.setContent(content);
        invalidate();
        return this;
    }

    /** Allocates layer depth steps for text and background elements. */
    @Override
    int layerSteps() {
        return hasBorder() ? 3 : 2;
    }

    @Override
    protected void invalidate() {
        super.invalidate();
        text = null;
        longestWord = -1;
    }

    private String renderText() {
        if (text == null) {
            text = component.renderText();
        }
        return text;
    }

    /** Calculates minimum content width of text in font pixels. */
    private int longestWord() {
        if (longestWord < 0) {
            longestWord = FontMetrics.minContentWidth(renderText(), component.textWrap());
        }
        return longestWord;
    }

    /** Calculates text line width in font pixels for available width. */
    private int lineWidthFor(double maxWidth) {
        int configured = Pixelay.settings().text().lineWidth();
        if (maxWidth < 0) {
            return configured;
        }
        // Calculate line width using character advances.
        // Float rounding tolerance prevents unexpected wrapping.
        int available = (int) Math.floor((maxWidth * FontMetrics.LINE_HEIGHT_PX) / component.heightPx() + 1e-6) + 1;
        if (component.textWrap().whiteSpace() == TextWrap.WhiteSpace.NOWRAP) {
            return configured;
        }
        if (component.textWrap().wordBreak() != TextWrap.WordBreak.NORMAL) {
            // Break words when configured to fit available space.
            return Math.min(configured, Math.max(1, available));
        }
        return Math.min(configured, Math.max(available, longestWord()));
    }

    @Override
    protected Size measureContent(double maxWidth) {
        return TextPlacement.boxSizePx(component, renderText(), lineWidthFor(maxWidth));
    }

    @Override
    protected double minContentWidth() {
        // Use explicit hitbox dimensions when both dimensions are set.
        if (component.hitboxWidthPx() > 0 && component.hitboxHeightPx() > 0) {
            return component.hitboxWidthPx();
        }
        // Advance without trailing spacing pixel.
        int word = Math.min(longestWord(), Pixelay.settings().text().lineWidth()) - 1;
        return (Math.max(0, word) * component.heightPx()) / FontMetrics.LINE_HEIGHT_PX;
    }

    @Override
    protected void arrangeContent(Rect content) {
        Align horizontal = justifySelf != null ? justifySelf : Align.START;
        Align vertical = alignSelf != null ? alignSelf : Align.START;
        HAlign h =
            textAlign != null
                ? textAlign
                : switch (horizontal) {
                      case START, STRETCH -> HAlign.LEFT;
                      case CENTER -> HAlign.CENTER;
                      case END -> HAlign.RIGHT;
                  };
        VAlign v = switch (vertical) {
            case START, STRETCH -> VAlign.TOP;
            case CENTER -> VAlign.MIDDLE;
            case END -> VAlign.BOTTOM;
        };
        double x = switch (h) {
            case LEFT -> content.x();
            case CENTER -> content.centerX();
            case RIGHT -> content.right();
        };
        double y = switch (v) {
            case TOP -> content.y();
            case MIDDLE -> content.centerY();
            case BOTTOM -> content.bottom();
        };
        // Apply final wrapping width to component.
        component.setLineWidth(lineWidthFor(content.width()));
        if (textOverflow == TextOverflow.ELLIPSIS && content.width() > 0) {
            int availFontPx =
                (int) Math.floor((content.width() * FontMetrics.LINE_HEIGHT_PX) / component.heightPx() + 1e-6) + 1;
            component.setEllipsisWidthFontPx(Math.max(1, availFontPx));
        } else {
            component.setEllipsisWidthFontPx(0);
        }
        component.setVisualTransform(
            visualScaleX(),
            visualScaleY(),
            visualRotateDeg(),
            visualRotateXDeg(),
            visualRotateYDeg(),
            visualPivotX(),
            visualPivotY()
        );
        component.place(x, y, h, v);
        component.setFit(fit);
    }

    /** Sets text overflow behavior to clip or ellipsis. */
    public Text textOverflow(TextOverflow textOverflow) {
        this.textOverflow = textOverflow != null ? textOverflow : TextOverflow.CLIP;
        if (textOverflow == TextOverflow.ELLIPSIS) {
            whiteSpace(TextWrap.WhiteSpace.NOWRAP);
            overflow(Overflow.HIDDEN);
        }
        invalidate();
        return this;
    }

    public TextOverflow textOverflow() {
        return textOverflow;
    }

    /** Returns click handler covering the full box area including padding. */
    @Override
    ClickHandler effectiveOnClick() {
        ClickHandler own = super.effectiveOnClick();
        if (own != null) {
            return own;
        }
        Consumer<Player> compClick = component.onClick();
        return compClick != null ? (player, x, y) -> compClick.accept(player) : null;
    }

    /** Sets whitespace wrapping mode. */
    public Text whiteSpace(TextWrap.WhiteSpace value) {
        component.whiteSpace(value);
        invalidate();
        return this;
    }

    /** Sets word breaking rules. */
    public Text wordBreak(TextWrap.WordBreak value) {
        component.wordBreak(value);
        invalidate();
        return this;
    }

    @Override
    protected void collectComponents(List<LayoutComponent> out) {
        out.add(component);
    }
}
