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
 * A text element inside a layout (like an HTML {@code <p>} or {@code <span>}). Its size is the measured
 * text block (or the component's explicit hitbox); the text wraps to the width the layout gives it,
 * never breaking words (like CSS).
 * <p>
 * Inside its rectangle the text is pinned horizontally by {@link #textAlign} (or {@link #justifySelf}
 * when {@code textAlign} is unset) and vertically by {@link #alignSelf}, top-left by default.
 */
public final class Text extends Box<Text> {

    private final AbstractCanvasComponent component;
    private HAlign textAlign;
    private TextOverflow textOverflow = TextOverflow.CLIP;
    // Per-pass caches: the text and its longest word are evaluated once per layout pass.
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

    /** Sets the horizontal text alignment inside this box (CSS {@code text-align}), independent of {@link #justifySelf}. */
    public Text textAlign(HAlign align) {
        this.textAlign = align;
        return this;
    }

    /** Sets the horizontal text alignment inside this box (CSS {@code text-align}), independent of {@link #justifySelf}. */
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

    /** Font size (line height in canvas px, CSS {@code font-size}). */
    public Text fontSize(double heightPx) {
        component.setHeightPx(heightPx);
        invalidate();
        return this;
    }

    public double fontSize() {
        return component.heightPx();
    }

    /** Updates the text content (MiniMessage). */
    public Text text(String miniMessage) {
        return text(TextContent.of(miniMessage));
    }

    /** Updates the text content (or dynamic {@code () -> "..."} lambda). */
    public Text text(TextContent content) {
        component.setContent(content);
        invalidate();
        return this;
    }

    /** The text background, optional border, and the text above it. */
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

    /** The widest piece of the text that can not break (CSS min-content), in font px. */
    private int longestWord() {
        if (longestWord < 0) {
            longestWord = FontMetrics.minContentWidth(renderText(), component.textWrap());
        }
        return longestWord;
    }

    /** TextDisplay line width (font px) for an available width; words are never broken. */
    private int lineWidthFor(double maxWidth) {
        int configured = Pixelay.settings().text().lineWidth();
        if (maxWidth < 0) {
            return configured;
        }
        // The line width counts the advance, which is 1 font px wider than the drawn text.
        // A small tolerance: a text given exactly its own width must not wrap because of float rounding.
        int available = (int) Math.floor((maxWidth * FontMetrics.LINE_HEIGHT_PX) / component.heightPx() + 1e-6) + 1;
        if (component.textWrap().whiteSpace() == TextWrap.WhiteSpace.NOWRAP) {
            return configured;
        }
        if (component.textWrap().wordBreak() != TextWrap.WordBreak.NORMAL) {
            // Words may break: the text takes exactly the space it gets.
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
        // Same rule as TextPlacement.boxSizePx: an explicit hitbox counts only when both sides are set.
        if (component.hitboxWidthPx() > 0 && component.hitboxHeightPx() > 0) {
            return component.hitboxWidthPx();
        }
        // The drawn word: its advance without the trailing 1 font px of spacing.
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
        // Only now the element gets its final width: the client must wrap where we measured.
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
            visualRotateYDeg()
        );
        component.place(x, y, h, v);
        component.setFit(fit);
    }

    /** CSS {@code text-overflow}: {@link TextOverflow#CLIP} (default) or {@link TextOverflow#ELLIPSIS} ({@code "..."}). */
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

    /** A clickable component (a button) is clicked on its whole box, padding included, not just its text. */
    @Override
    ClickHandler effectiveOnClick() {
        ClickHandler own = super.effectiveOnClick();
        if (own != null) {
            return own;
        }
        Consumer<Player> compClick = component.onClick();
        return compClick != null ? (player, x, y) -> compClick.accept(player) : null;
    }

    /** CSS {@code white-space} of the text. */
    public Text whiteSpace(TextWrap.WhiteSpace value) {
        component.whiteSpace(value);
        invalidate();
        return this;
    }

    /** CSS {@code word-break} / {@code overflow-wrap} of the text. */
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
