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
 * Handles line breaking for canvas text in CSS style.
 * The engine writes line breaks into the component so the client does not wrap text.
 * Breaks occur at spaces, hyphens, or between any characters when configured.
 */
final class TextLayout {

    /** Represents one character with its advance and vertical bounds in font pixels. */
    record Glyph(int codePoint, int advance, int top, int bottom) {
        boolean space() {
            return codePoint == ' ';
        }

        boolean newline() {
            return codePoint == '\n';
        }
    }

    /**
     * @param lines line ranges by glyph index
     * @param dropped indices of spaces replaced by line breaks
     * @param breaksBefore indices of glyphs where line breaks are inserted
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

    /** Breaks glyphs into lines using {@code maxWidth} in font pixels. */
    static Lines lay(List<Glyph> glyphs, int maxWidth, TextWrap wrap) {
        List<int[]> lines = new ArrayList<>();
        Set<Integer> dropped = new HashSet<>();
        Set<Integer> breaksBefore = new HashSet<>();
        boolean wraps = wrap.whiteSpace() == TextWrap.WhiteSpace.NORMAL;
        int start = 0;
        int width = 0;
        // Index of the last possible break position and drop flag.
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
                // Spaces hang at line ends without causing overflow.
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
                    // The word exceeds line width, break inside the word.
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

    /** Calculates width, line count, and vertical bounds for laid-out text. */
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

    /** Calculates the minimum text width without overflow. */
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

    /** Truncates a single-line component with an ellipsis when width exceeds {@code maxWidth}. */
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

    /** Inserts line breaks into the component while preserving text styles. */
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

    /** Default text color for displays without an explicit color. */
    private static final int DEFAULT_COLOR = 0xFFFFFF;

    /**
     * Calculates bounding boxes for rendered glyphs based on alignment.
     * Returns boxes aligned with glyph indices.
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
                // Glyph width equals advance minus one font pixel spacing.
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

    /** Encodes shader identifier data into the lower color bits of each glyph. */
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
            // Move characters into child components with encoded colors.
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
