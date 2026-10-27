package dev.horuse.pixelay.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Line breaking of canvas text, CSS-style (see {@link TextWrap}). The same breaks are used to measure the
 * text and to render it: they are written into the component as line breaks, so the client never wraps
 * by itself.
 * <p>
 * Breaks are allowed at spaces (the space is dropped), after a hyphen (it stays at the line end) and, with
 * {@link TextWrap.WordBreak#BREAK_ALL}, between any two characters. A space never makes a line overflow.
 */
final class TextLayout {

    /** One character: its advance and the rows it is drawn on (font px from the line top). */
    record Glyph(int codePoint, int advance, int top, int bottom) {
        boolean space() {
            return codePoint == ' ';
        }

        boolean newline() {
            return codePoint == '\n';
        }
    }

    /**
     * @param lines {@code [start, end)} glyph ranges of every line
     * @param dropped indexes of spaces replaced by a line break
     * @param breaksBefore indexes of glyphs a line break is inserted before
     */
    record Lines(List<Glyph> glyphs, List<int[]> lines, Set<Integer> dropped, Set<Integer> breaksBefore) {}

    private TextLayout() {}

    static List<Glyph> glyphs(Component root) {
        List<Glyph> out = new ArrayList<>();
        collect(root, false, out);
        return out;
    }

    private static void collect(Component component, boolean parentBold, List<Glyph> out) {
        TextDecoration.State state = component.style().decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.TRUE || (state != TextDecoration.State.FALSE && parentBold);
        if (component instanceof TextComponent text) {
            String content = text.content();
            for (int i = 0; i < content.length(); ) {
                int codePoint = content.codePointAt(i);
                if (codePoint == '\n') {
                    out.add(new Glyph('\n', 0, 0, (int) FontMetrics.LINE_HEIGHT_PX));
                } else {
                    int[] rows = FontMetrics.verticalOf(codePoint);
                    out.add(new Glyph(codePoint, FontMetrics.advance(codePoint, bold), rows[0], rows[1]));
                }
                i += Character.charCount(codePoint);
            }
        }
        for (Component child : component.children()) {
            collect(child, bold, out);
        }
    }

    /** Breaks the glyphs into lines of at most {@code maxWidth} font px (when they can break). */
    static Lines lay(List<Glyph> glyphs, int maxWidth, TextWrap wrap) {
        List<int[]> lines = new ArrayList<>();
        Set<Integer> dropped = new HashSet<>();
        Set<Integer> breaksBefore = new HashSet<>();
        boolean wraps = wrap.whiteSpace() == TextWrap.WhiteSpace.NORMAL;
        int start = 0;
        int width = 0;
        // Last place the line may break: the glyph index the next line starts at, and whether a space is dropped.
        int breakAt = -1;
        boolean breakDrops = false;

        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            if (glyph.newline()) {
                lines.add(new int[] { start, i });
                start = i + 1;
                width = 0;
                breakAt = -1;
                continue;
            }
            if (!wraps) {
                width += glyph.advance();
                continue;
            }
            if (glyph.space()) {
                // Spaces hang at the line end: they never overflow it, and a break there drops them.
                width += glyph.advance();
                breakAt = i;
                breakDrops = true;
                continue;
            }
            if (width + glyph.advance() > maxWidth && width > 0) {
                if (breakAt > start) {
                    lines.add(new int[] { start, breakAt });
                    if (breakDrops) {
                        dropped.add(breakAt);
                        start = breakAt + 1;
                    } else {
                        breaksBefore.add(breakAt);
                        start = breakAt;
                    }
                    width = widthOf(glyphs, start, i);
                    breakAt = -1;
                }
                if (width + glyph.advance() > maxWidth && width > 0 && wrap.wordBreak() != TextWrap.WordBreak.NORMAL) {
                    // The word alone is too wide: break inside it.
                    lines.add(new int[] { start, i });
                    breaksBefore.add(i);
                    start = i;
                    width = 0;
                    breakAt = -1;
                }
            }
            width += glyph.advance();
            if (glyph.codePoint() == '-' || wrap.wordBreak() == TextWrap.WordBreak.BREAK_ALL) {
                breakAt = i + 1;
                breakDrops = false;
            }
        }
        lines.add(new int[] { start, glyphs.size() });
        return new Lines(glyphs, lines, dropped, breaksBefore);
    }

    private static int widthOf(List<Glyph> glyphs, int from, int to) {
        int width = 0;
        for (int i = from; i < to; i++) {
            width += glyphs.get(i).advance();
        }
        return width;
    }

    /** Width, line count and vertical extent of laid-out text. */
    static FontMetrics.Metrics metrics(Lines laid) {
        int widest = 0;
        double top = Double.MAX_VALUE;
        double bottom = -Double.MAX_VALUE;
        for (int n = 0; n < laid.lines().size(); n++) {
            int[] line = laid.lines().get(n);
            int width = 0;
            int lineTop = Integer.MAX_VALUE;
            int lineBottom = Integer.MIN_VALUE;
            for (int i = line[0]; i < line[1]; i++) {
                Glyph glyph = laid.glyphs().get(i);
                width += glyph.advance();
                if (!glyph.space()) {
                    lineTop = Math.min(lineTop, glyph.top());
                    lineBottom = Math.max(lineBottom, glyph.bottom());
                }
            }
            widest = Math.max(widest, width);
            boolean empty = lineTop > lineBottom;
            double lineStart = n * FontMetrics.LINE_PITCH_PX;
            top = Math.min(top, lineStart + (empty ? 0 : lineTop));
            bottom = Math.max(bottom, lineStart + (empty ? FontMetrics.LINE_HEIGHT_PX : lineBottom));
        }
        return new FontMetrics.Metrics(widest, laid.lines().size(), top, bottom);
    }

    /**
     * The narrowest width (font px) the text can take without overflowing: its widest unbreakable piece
     * (CSS min-content). Words with {@code BREAK_WORD} still count whole, as in CSS.
     */
    static int minContentWidth(List<Glyph> glyphs, TextWrap wrap) {
        int widest = 0;
        int piece = 0;
        boolean wraps = wrap.whiteSpace() == TextWrap.WhiteSpace.NORMAL;
        for (Glyph glyph : glyphs) {
            if (glyph.newline() || (wraps && glyph.space())) {
                widest = Math.max(widest, piece);
                piece = 0;
                continue;
            }
            piece += glyph.advance();
            if (wraps && (glyph.codePoint() == '-' || wrap.wordBreak() == TextWrap.WordBreak.BREAK_ALL)) {
                widest = Math.max(widest, piece);
                piece = 0;
            }
        }
        return Math.max(widest, piece);
    }

    /**
     * Truncates a single-line MiniMessage component with {@code "..."} if its width exceeds {@code maxWidth} font px.
     */
    static Component truncateEllipsis(Component root, int maxWidth) {
        if (maxWidth <= 0) {
            return Component.empty();
        }
        List<Glyph> glyphs = glyphs(root);
        int total = widthOf(glyphs, 0, glyphs.size());
        if (total <= maxWidth) {
            return root;
        }
        int dotAdvance = FontMetrics.advance('.', false);
        int ellipsisWidth = dotAdvance * 3;
        if (maxWidth <= ellipsisWidth) {
            return Component.text("...");
        }
        int budget = maxWidth - ellipsisWidth;
        int keepCount = 0;
        int used = 0;
        for (Glyph glyph : glyphs) {
            if (glyph.newline() || used + glyph.advance() > budget) {
                break;
            }
            used += glyph.advance();
            keepCount++;
        }
        return truncateTree(root, new int[] { keepCount }, new boolean[] { false });
    }

    private static Component truncateTree(Component component, int[] remaining, boolean[] ellipsisAppended) {
        if (remaining[0] <= 0 && ellipsisAppended[0]) {
            return Component.empty();
        }
        Component result = component;
        if (component instanceof TextComponent text) {
            String content = text.content();
            StringBuilder out = new StringBuilder(content.length() + 3);
            for (int i = 0; i < content.length(); ) {
                if (remaining[0] <= 0) {
                    if (!ellipsisAppended[0]) {
                        out.append("...");
                        ellipsisAppended[0] = true;
                    }
                    break;
                }
                int codePoint = content.codePointAt(i);
                out.appendCodePoint(codePoint);
                remaining[0]--;
                i += Character.charCount(codePoint);
                if (remaining[0] == 0 && !ellipsisAppended[0]) {
                    out.append("...");
                    ellipsisAppended[0] = true;
                }
            }
            result = text.content(out.toString());
        }
        if (component.children().isEmpty()) {
            return result;
        }
        List<Component> children = new ArrayList<>();
        for (Component child : component.children()) {
            if (remaining[0] <= 0 && ellipsisAppended[0]) {
                break;
            }
            children.add(truncateTree(child, remaining, ellipsisAppended));
        }
        return result.children(children);
    }

    /** Writes the breaks into the component as line breaks, keeping every style. */
    static Component apply(Component root, Lines laid) {
        if (laid.dropped().isEmpty() && laid.breaksBefore().isEmpty()) {
            return root;
        }
        return rewrite(root, laid, new int[] { 0 });
    }

    private static Component rewrite(Component component, Lines laid, int[] index) {
        Component result = component;
        if (component instanceof TextComponent text) {
            String content = text.content();
            StringBuilder out = new StringBuilder(content.length() + 4);
            for (int i = 0; i < content.length(); ) {
                int codePoint = content.codePointAt(i);
                int at = index[0]++;
                if (laid.breaksBefore().contains(at)) {
                    out.append('\n');
                }
                if (laid.dropped().contains(at)) {
                    out.append('\n');
                } else {
                    out.appendCodePoint(codePoint);
                }
                i += Character.charCount(codePoint);
            }
            result = text.content(out.toString());
        }
        if (component.children().isEmpty()) {
            return result;
        }
        List<Component> children = new ArrayList<>(component.children().size());
        for (Component child : component.children()) {
            children.add(rewrite(child, laid, index));
        }
        return result.children(children);
    }

    /** Text colour when nothing sets one: TextDisplays draw white. */
    private static final int DEFAULT_COLOR = 0xFFFFFF;

    /**
     * Where every glyph is drawn, in font px from the left of the block and the top of its first line, as the
     * client lays the lines out: each one pressed left, centred or pressed right inside the widest line.
     * Index-aligned with {@link #glyphs}; {@code null} for glyphs that draw nothing (spaces, line breaks).
     */
    static List<GlyphBox> boxes(Lines laid, LineAlign align) {
        List<GlyphBox> boxes = new ArrayList<>(laid.glyphs().size());
        for (int i = 0; i < laid.glyphs().size(); i++) {
            boxes.add(null);
        }
        int widest = 0;
        for (int[] line : laid.lines()) {
            widest = Math.max(widest, widthOf(laid.glyphs(), line[0], line[1]));
        }
        for (int n = 0; n < laid.lines().size(); n++) {
            int[] line = laid.lines().get(n);
            int width = widthOf(laid.glyphs(), line[0], line[1]);
            double pen = switch (align) {
                case LEFT -> 0;
                case CENTER -> (widest - width) / 2.0;
                case RIGHT -> widest - width;
            };
            double lineTop = n * FontMetrics.LINE_PITCH_PX;
            for (int i = line[0]; i < line[1]; i++) {
                Glyph glyph = laid.glyphs().get(i);
                // A bitmap glyph draws its advance minus the 1 font px of spacing (bold: one more, its copy).
                int ink = glyph.advance() - 1;
                if (!glyph.space() && ink > 0) {
                    int[] rows = FontMetrics.quadRowsOf(glyph.codePoint());
                    boxes.set(i, new GlyphBox(pen, lineTop + rows[0], pen + ink, lineTop + rows[1]));
                }
                pen += glyph.advance();
            }
        }
        return boxes;
    }

    /**
     * The laid-out component (see {@link #apply}) with every glyph in its own colour: the glyph's colour with
     * its 3 lowest bits of red, green and blue replaced by {@code codes[i]} (9 bits), so that a core shader can
     * read them. The colours change by at most 7/255.
     */
    static Component encode(Component root, Lines laid, int[] codes) {
        return encode(root, laid, codes, new int[] { 0 }, DEFAULT_COLOR);
    }

    private static Component encode(Component component, Lines laid, int[] codes, int[] index, int inherited) {
        int color = component.style().color() != null ? component.style().color().value() : inherited;
        List<Component> children = new ArrayList<>();
        Component result = component;
        if (component instanceof TextComponent text) {
            String content = text.content();
            for (int i = 0; i < content.length(); ) {
                int codePoint = content.codePointAt(i);
                int at = index[0]++;
                if (laid.breaksBefore().contains(at)) {
                    children.add(Component.text("\n"));
                }
                String piece = laid.dropped().contains(at) ? "\n" : new String(Character.toChars(codePoint));
                children.add(Component.text(piece, TextColor.color(withCode(color, codes[at]))));
                i += Character.charCount(codePoint);
            }
            // The text moves into the children, which inherit every other style of the component.
            result = text.content("");
        }
        for (Component child : component.children()) {
            children.add(encode(child, laid, codes, index, color));
        }
        return result.children(children);
    }

    private static int withCode(int rgb, int code) {
        int red = ((rgb >> 16) & 0xF8) | (code & 7);
        int green = ((rgb >> 8) & 0xF8) | ((code >> 3) & 7);
        int blue = (rgb & 0xF8) | ((code >> 6) & 7);
        return (red << 16) | (green << 8) | blue;
    }
}
