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
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * A text node inside a layout (like an HTML {@code <p>} or {@code <span>}). Its size is the measured
 * text block (or the component's explicit hitbox); the text wraps to the width the layout gives it,
 * never breaking words (like CSS).
 * <p>
 * Inside its rectangle the text is pinned horizontally by {@link #textAlign} (or {@link #justifySelf}
 * when {@code textAlign} is unset) and vertically by {@link #alignSelf}, top-left by default.
 */
public final class Leaf extends Box<Leaf> {

    private final AbstractCanvasComponent component;
    private HAlign textAlign;
    // Per-pass caches: the text and its longest word are evaluated once per layout pass.
    private String text;
    private int longestWord = -1;

    public Leaf(AbstractCanvasComponent component) {
        this.component = component;
    }

    public Leaf(Player viewer, double heightPx, TextContent content) {
        this(
            viewer != null ? CanvasText.fake(viewer, 0, 0, heightPx, content) : new CanvasText(0, 0, heightPx, content)
        );
    }

    public Leaf(Player viewer, double heightPx, String miniMessage) {
        this(viewer, heightPx, TextContent.of(miniMessage));
    }

    public Leaf(Player viewer, double heightPx, Supplier<String> supplier) {
        this(viewer, heightPx, TextContent.supplier(supplier));
    }

    @Override
    public String tagName() {
        return "text";
    }

    @Override
    public AbstractCanvasComponent component() {
        return component;
    }

    /** Sets the horizontal text alignment inside this box (CSS {@code text-align}), independent of {@link #justifySelf}. */
    public Leaf textAlign(HAlign align) {
        this.textAlign = align;
        return this;
    }

    /** Sets the horizontal text alignment inside this box (CSS {@code text-align}), independent of {@link #justifySelf}. */
    public Leaf textAlign(Align align) {
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
    public Leaf fontSize(double heightPx) {
        component.heightPx(heightPx);
        invalidate();
        return this;
    }

    public double fontSize() {
        return component.heightPx();
    }

    /** Updates the text content (MiniMessage). */
    public Leaf text(String miniMessage) {
        return text(TextContent.of(miniMessage));
    }

    /** Updates the dynamic text content supplier. */
    public Leaf text(Supplier<String> supplier) {
        return text(TextContent.supplier(supplier));
    }

    /** Updates the text content. */
    public Leaf text(TextContent content) {
        component.content(content);
        invalidate();
        return this;
    }

    /** The text background, and the text one step above it. */
    @Override
    int layerSteps() {
        return 2;
    }

    @Override
    protected void invalidate() {
        super.invalidate();
        text = null;
        longestWord = -1;
    }

    private String text() {
        if (text == null) {
            text = component.renderText();
        }
        return text;
    }

    /** The widest piece of the text that can not break (CSS min-content), in font px. */
    private int longestWord() {
        if (longestWord < 0) {
            longestWord = FontMetrics.minContentWidth(text(), component.textWrap());
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
        return TextPlacement.boxSizePx(component, text(), lineWidthFor(maxWidth));
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
        component.place(x, y, h, v);
        component.setFit(fit);
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
    public Leaf whiteSpace(TextWrap.WhiteSpace value) {
        component.whiteSpace(value);
        invalidate();
        return this;
    }

    /** CSS {@code word-break} / {@code overflow-wrap} of the text. */
    public Leaf wordBreak(TextWrap.WordBreak value) {
        component.wordBreak(value);
        invalidate();
        return this;
    }

    @Override
    protected void collectComponents(List<LayoutComponent> out) {
        out.add(component);
    }
}
