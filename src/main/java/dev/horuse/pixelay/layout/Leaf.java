package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.components.AbstractCanvasComponent;
import dev.horuse.pixelay.components.CanvasLayout;
import dev.horuse.pixelay.content.FontMetrics;
import dev.horuse.pixelay.data.HAlign;
import dev.horuse.pixelay.data.VAlign;
import java.util.List;

/**
 * A canvas component inside a layout. Its size is the measured text block (or the component's explicit
 * hitbox); the text wraps to the width the layout gives it, never breaking words (like CSS).
 * <p>
 * Inside its rectangle the text is pinned by {@link #justifySelf} (horizontal) and {@link #alignSelf}
 * (vertical), top-left by default; {@code STRETCH} counts as start. In a row or a column the parent
 * ignores {@code justifySelf} (as CSS flexbox does), but the leaf still uses it for its text. The
 * layout owns the component's position and alignment, so {@code component.align(...)} has no effect.
 */
public final class Leaf extends Box<Leaf> {

    private final AbstractCanvasComponent component;
    // Per-pass caches: the text and its longest word are evaluated once per layout pass.
    private String text;
    private int longestWord = -1;

    Leaf(AbstractCanvasComponent component) {
        this.component = component;
    }

    public AbstractCanvasComponent component() {
        return component;
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

    private int longestWord() {
        if (longestWord < 0) {
            longestWord = FontMetrics.longestWordWidth(text());
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
        int available = (int) Math.floor((maxWidth * FontMetrics.LINE_HEIGHT_PX) / component.heightPx()) + 1;
        return Math.min(configured, Math.max(available, longestWord()));
    }

    @Override
    protected Size measureContent(double maxWidth) {
        double[] size = CanvasLayout.hitboxSizePx(component, text(), lineWidthFor(maxWidth));
        return new Size(size[0], size[1]);
    }

    @Override
    protected double minContentWidth() {
        // Same rule as CanvasLayout.hitboxSizePx: an explicit hitbox counts only when both sides are set.
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
        HAlign h = switch (horizontal) {
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
        component.setOverflow(overflow);
    }

    @Override
    protected void collectComponents(List<AbstractCanvasComponent> out) {
        out.add(component);
    }
}
