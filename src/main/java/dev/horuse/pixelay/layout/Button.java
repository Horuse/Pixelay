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
 * Interactive button layout element.
 * Wraps a text component with CSS box model support.
 * Defaults to centered text, pointer cursor, and standard padding.
 */
public final class Button extends Box<Button> {

    private final AbstractCanvasComponent component;
    private HAlign textAlign = HAlign.CENTER;
    private VAlign verticalTextAlign = VAlign.MIDDLE;
    private String text;
    private int longestWord = -1;

    public Button(AbstractCanvasComponent component) {
        this.component = component;
        padding(8, 16);
        cursor("pointer");
    }

    public Button(Player viewer, double heightPx, TextContent content, Consumer<Player> onClick) {
        this(
            viewer != null ? CanvasText.fake(viewer, 0, 0, heightPx, content) : new CanvasText(0, 0, heightPx, content)
        );
        if (onClick != null) {
            onClick(onClick);
        }
    }

    public Button(Player viewer, double heightPx, String miniMessage, Consumer<Player> onClick) {
        this(viewer, heightPx, TextContent.of(miniMessage), onClick);
    }

    public Button(double heightPx, TextContent content, Consumer<Player> onClick) {
        this(null, heightPx, content, onClick);
    }

    public Button(double heightPx, String miniMessage, Consumer<Player> onClick) {
        this(null, heightPx, TextContent.of(miniMessage), onClick);
    }

    public Button(Player viewer, String miniMessage, Consumer<Player> onClick) {
        this(viewer, 20, TextContent.of(miniMessage), onClick);
    }

    public Button(String miniMessage, Consumer<Player> onClick) {
        this(null, 20, TextContent.of(miniMessage), onClick);
    }

    @Override
    void bindViewer(Player viewer) {
        if (component instanceof CanvasText canvasText) {
            canvasText.bindViewer(viewer);
        }
    }

    @Override
    public String tagName() {
        return "button";
    }

    @Override
    public AbstractCanvasComponent component() {
        return component;
    }

    @Override
    int layerSteps() {
        return 2;
    }

    /** Sets horizontal alignment of button label within its content box. */
    public Button textAlign(HAlign align) {
        this.textAlign = align != null ? align : HAlign.CENTER;
        return this;
    }

    /** Sets horizontal alignment of button label using layout alignment. */
    public Button textAlign(Align align) {
        this.textAlign =
            align == null
                ? HAlign.CENTER
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

    /** Sets vertical alignment of button label within its content box. */
    public Button verticalTextAlign(VAlign align) {
        this.verticalTextAlign = align != null ? align : VAlign.MIDDLE;
        return this;
    }

    public VAlign verticalTextAlign() {
        return verticalTextAlign;
    }

    /** Sets font size in canvas pixels. */
    public Button fontSize(double heightPx) {
        component.setHeightPx(heightPx);
        invalidate();
        return this;
    }

    public double fontSize() {
        return component.heightPx();
    }

    /** Updates button label text. */
    public Button text(String miniMessage) {
        return text(TextContent.of(miniMessage));
    }

    /** Updates button label text content. */
    public Button text(TextContent content) {
        component.setContent(content);
        invalidate();
        return this;
    }

    /** Sets whitespace handling mode for button label. */
    public Button whiteSpace(TextWrap.WhiteSpace value) {
        component.whiteSpace(value);
        invalidate();
        return this;
    }

    /** Sets word break mode for button label. */
    public Button wordBreak(TextWrap.WordBreak value) {
        component.wordBreak(value);
        invalidate();
        return this;
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

    private int longestWord() {
        if (longestWord < 0) {
            longestWord = FontMetrics.minContentWidth(renderText(), component.textWrap());
        }
        return longestWord;
    }

    private int lineWidthFor(double maxWidth) {
        int configured = Pixelay.settings().text().lineWidth();
        if (maxWidth < 0) {
            return configured;
        }
        int available = (int) Math.floor((maxWidth * FontMetrics.LINE_HEIGHT_PX) / component.heightPx() + 1e-6) + 1;
        if (component.textWrap().whiteSpace() == TextWrap.WhiteSpace.NOWRAP) {
            return configured;
        }
        if (component.textWrap().wordBreak() != TextWrap.WordBreak.NORMAL) {
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
        if (component.hitboxWidthPx() > 0 && component.hitboxHeightPx() > 0) {
            return component.hitboxWidthPx();
        }
        int word = Math.min(longestWord(), Pixelay.settings().text().lineWidth()) - 1;
        return (Math.max(0, word) * component.heightPx()) / FontMetrics.LINE_HEIGHT_PX;
    }

    @Override
    protected void arrangeContent(Rect content) {
        HAlign h = textAlign != null ? textAlign : HAlign.CENTER;
        VAlign v = verticalTextAlign != null ? verticalTextAlign : VAlign.MIDDLE;
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
        component.setLineWidth(lineWidthFor(content.width()));
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

    @Override
    ClickHandler effectiveOnClick() {
        ClickHandler own = super.effectiveOnClick();
        if (own != null) {
            return own;
        }
        Consumer<Player> compClick = component.onClick();
        return compClick != null ? (player, x, y) -> compClick.accept(player) : null;
    }

    @Override
    protected void collectComponents(List<LayoutComponent> out) {
        out.add(component);
    }
}
