package dev.horuse.pixelay.hud;

import dev.horuse.pixelay.layout.Box;
import dev.horuse.pixelay.layout.Container;
import dev.horuse.pixelay.layout.Flex;
import dev.horuse.pixelay.layout.Insets;
import dev.horuse.pixelay.layout.Layout;
import dev.horuse.pixelay.layout.LayoutItem;
import dev.horuse.pixelay.layout.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Renders Pixelay layout trees as an on-screen HUD element.
 * Supports flex rows, columns, gaps, MiniMessage styling, and PlaceholderAPI.
 *
 * <p>The root box follows the CSS box model relative to the anchor:
 * {@code margin} is the distance from the screen edge to the box edge, {@code padding} is the
 * space between the border and the text, and {@code background}, {@code border} and
 * {@code borderRadius} draw a panel behind the text. Bottom anchors grow upward, so the box
 * bottom always stays at the margin. Every line and the panel return the cursor to the start, so the
 * element never moves other elements in the same bossbar row.
 */
public class LayoutHudElement extends BasicHudElement {

    /** Vertical distance between two text lines without column gap. */
    public static final int LINE_HEIGHT = 10;
    /** Visible height of a text line (cap height without the descender row). */
    public static final int GLYPH_HEIGHT = 7;
    /** Distance between the decoded font Y and the drawn glyph top in the bossbar row. */
    public static final int GLYPH_TOP_OFFSET = 3;

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Function<Player, LayoutItem> layoutSupplier;
    private final boolean hasPapi;
    private boolean placeholders = true;

    /**
     * Creates a HUD element from a static layout tree.
     * Registers the fonts and the panel of the tree so they are present in the pack.
     *
     * @param anchor target screen anchor
     * @param offsetX horizontal pixel offset
     * @param offsetY vertical pixel offset
     * @param root layout item root
     */
    public LayoutHudElement(HudAnchor anchor, int offsetX, int offsetY, LayoutItem root) {
        this(anchor, offsetX, offsetY, player -> root);
        if (root != null) {
            preload(root);
        }
    }

    /**
     * Creates a HUD element with default zero offsets.
     *
     * @param anchor target screen anchor
     * @param root layout item root
     */
    public LayoutHudElement(HudAnchor anchor, LayoutItem root) {
        this(anchor, 0, 0, root);
    }

    /**
     * Creates a HUD element from a dynamic per-player layout function.
     * Call {@link #preload(LayoutItem)} with a sample tree before the pack is built.
     *
     * @param anchor target screen anchor
     * @param offsetX horizontal pixel offset
     * @param offsetY vertical pixel offset
     * @param layoutSupplier function supplying layout tree per player
     */
    public LayoutHudElement(HudAnchor anchor, int offsetX, int offsetY, Function<Player, LayoutItem> layoutSupplier) {
        super(anchor, offsetX, offsetY, Component.empty());
        this.layoutSupplier = Objects.requireNonNull(layoutSupplier, "layoutSupplier");
        this.hasPapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    /**
     * Registers the line fonts and the background panel that a tree of this shape needs.
     * Only the line count and the box styles matter; the text can change later.
     *
     * @param sample a tree with the same shape as the rendered trees
     */
    public void preload(LayoutItem sample) {
        Box<?> root = Layout.box(sample);
        List<List<LineItem>> lines = new ArrayList<>();
        collectLines(root, lines);
        if (lines.isEmpty()) {
            return;
        }
        Frame frame = frame(root, lines.size(), 0);
        for (int i = 0; i < lines.size(); i++) {
            YFontRegistry.register(anchor(), frame.lineY(i));
        }
        HudPanel.Style panel = panelStyle(root, frame);
        if (panel.visible()) {
            HudPanel.register(anchor(), frame.top, panel);
        }
    }

    /**
     * Enables or disables PlaceholderAPI for the text of this element. Disable it when the text
     * comes from untrusted input, so {@code %placeholder%} sequences stay plain text.
     *
     * @param enabled true to resolve placeholders (default)
     * @return this element
     */
    public LayoutHudElement placeholders(boolean enabled) {
        this.placeholders = enabled;
        return this;
    }

    @Override
    public Component render() {
        return super.render();
    }

    @Override
    public Component renderFor(Player player) {
        if (player == null || !player.isOnline()) {
            return Component.empty();
        }

        LayoutItem item = layoutSupplier.apply(player);
        if (item == null) {
            return Component.empty();
        }

        Box<?> rootBox = Layout.box(item);
        List<List<LineItem>> lines = new ArrayList<>();
        collectLines(rootBox, lines);
        if (lines.isEmpty()) {
            return Component.empty();
        }

        List<List<Piece>> pieces = new ArrayList<>(lines.size());
        for (List<LineItem> line : lines) {
            pieces.add(encodePieces(line, player));
        }
        // Rows with several pieces form a grid: each column is as wide as its widest piece,
        // so every grid row has the same width and the columns line up.
        List<Integer> columns = new ArrayList<>();
        for (List<Piece> line : pieces) {
            if (line.size() < 2) {
                continue;
            }
            for (int i = 0; i < line.size(); i++) {
                if (columns.size() <= i) {
                    columns.add(0);
                }
                columns.set(i, Math.max(columns.get(i), line.get(i).width()));
            }
        }
        List<EncodedLine> encoded = new ArrayList<>(lines.size());
        int contentWidth = 0;
        for (List<Piece> line : pieces) {
            EncodedLine e = joinPieces(line, columns);
            encoded.add(e);
            contentWidth = Math.max(contentWidth, e.width);
        }
        if (contentWidth <= 0) {
            return Component.empty();
        }

        Frame frame = frame(rootBox, lines.size(), contentWidth);
        TextComponent.Builder builder = Component.text();

        builder.append(HudPanel.render(anchor(), frame.left, frame.top, frame.width, panelStyle(rootBox, frame)));

        for (int i = 0; i < encoded.size(); i++) {
            EncodedLine line = encoded.get(i);
            if (line.width <= 0) {
                continue;
            }
            int x = lineX(frame, line.width);
            builder
                .append(HudSpace.offset(x))
                .append(line.component.font(HudPosition.font(anchor(), frame.lineY(i))))
                .append(HudSpace.offset(-x - line.width));
        }

        return builder.build();
    }

    private Frame frame(Box<?> root, int lineCount, int contentWidth) {
        Insets pad = root.padding();
        Insets margin = root.margin();
        int step = LINE_HEIGHT + (int) Math.round(lineGap(root));
        // CSS box model: the border sits between the margin and the padding.
        int border = borderWidth(root);
        int padTop = (int) Math.round(pad.top()) + border;
        int padLeft = (int) Math.round(pad.left()) + border;
        int padRight = (int) Math.round(pad.right()) + border;
        int padBottom = (int) Math.round(pad.bottom()) + border;

        int contentHeight = (Math.max(1, lineCount) - 1) * step + GLYPH_HEIGHT;
        int width = contentWidth + padLeft + padRight;
        int height = contentHeight + padTop + padBottom;
        if (root.fixedWidth() >= 0) {
            width = (int) Math.round(root.fixedWidth());
        }
        if (root.fixedHeight() >= 0) {
            height = (int) Math.round(root.fixedHeight());
        }
        width = clamp(width, root.minWidth(), root.maxWidth());
        height = clamp(height, root.minHeight(), root.maxHeight());

        int left;
        if (anchor().isRightAligned()) {
            left = offsetX() - (int) Math.round(margin.right()) - width;
        } else if (anchor().isCenterAligned()) {
            left = offsetX() + (int) Math.round(margin.left() - margin.right()) / 2 - width / 2;
        } else {
            left = offsetX() + (int) Math.round(margin.left());
        }

        int top;
        if (anchor().isBottomAnchored()) {
            top = offsetY() - (int) Math.round(margin.bottom()) - height - GLYPH_TOP_OFFSET;
        } else if (isVerticallyCentered(anchor())) {
            top = offsetY() + (int) Math.round(margin.top() - margin.bottom()) / 2 - height / 2 - GLYPH_TOP_OFFSET;
        } else {
            top = offsetY() + (int) Math.round(margin.top()) - GLYPH_TOP_OFFSET;
        }

        // Text sits in the vertical middle of the content area when a fixed height is larger.
        int textTop = top + padTop + Math.max(0, (height - padTop - padBottom - contentHeight) / 2);
        return new Frame(left, top, width, height, textTop, step, padLeft, padRight);
    }

    /** Aligns a line to the anchor side inside the box (left, center or right). */
    private int lineX(Frame frame, int lineWidth) {
        if (anchor().isRightAligned()) {
            return frame.left() + frame.width() - frame.padRight() - lineWidth;
        }
        if (anchor().isCenterAligned()) {
            int inner = frame.width() - frame.padLeft() - frame.padRight();
            return frame.left() + frame.padLeft() + (inner - lineWidth) / 2;
        }
        return frame.left() + frame.padLeft();
    }

    private static boolean isVerticallyCentered(HudAnchor anchor) {
        return anchor == HudAnchor.CENTER_LEFT || anchor == HudAnchor.CENTER || anchor == HudAnchor.CENTER_RIGHT;
    }

    private static int clamp(int value, double min, double max) {
        int result = value;
        if (min >= 0) {
            result = Math.max(result, (int) Math.round(min));
        }
        if (max >= 0) {
            result = Math.min(result, (int) Math.round(max));
        }
        return result;
    }

    private static double lineGap(Box<?> root) {
        if (root instanceof Flex flex && !flex.isRow()) {
            return flex.gap();
        }
        return 0;
    }

    private static HudPanel.Style panelStyle(Box<?> root, Frame frame) {
        int border = borderWidth(root);
        return new HudPanel.Style(
            frame.height(),
            (int) Math.round(root.borderRadius()),
            withOpacity(root.background(), root.opacity()),
            border,
            withOpacity(root.borderColor(), root.opacity())
        );
    }

    /** Uniform border width; Screen HUD panels use the top border width for every side. */
    private static int borderWidth(Box<?> root) {
        return Math.max(0, (int) Math.round(root.borderWidths().top()));
    }

    private static int withOpacity(int argb, double opacity) {
        int alpha = (int) Math.round(((argb >>> 24) & 0xFF) * opacity);
        return (Math.clamp(alpha, 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    private List<Piece> encodePieces(List<LineItem> items, Player player) {
        List<Piece> pieces = new ArrayList<>(items.size());
        for (LineItem item : items) {
            String rawText = item.text.renderText();
            if (rawText == null || rawText.isEmpty()) {
                continue;
            }
            if (hasPapi && placeholders) {
                rawText = PlaceholderAPI.setPlaceholders(player, rawText);
            }
            Component parsed = MINI_MESSAGE.deserialize(LegacyText.toMiniMessage(rawText));
            int[] widthTracker = new int[] { 0 };
            Component encoded = HudCodec.encodeComponent(parsed, anchor(), 0, widthTracker);
            int gap = pieces.isEmpty() ? 0 : Math.max(0, (int) Math.round(item.gapBefore));
            pieces.add(new Piece(encoded, widthTracker[0], gap));
        }
        return pieces;
    }

    /** Joins the pieces of one line; a piece in a grid column is padded to the column width. */
    private static EncodedLine joinPieces(List<Piece> pieces, List<Integer> columns) {
        TextComponent.Builder lineBuilder = Component.text();
        int totalWidth = 0;
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            if (piece.gapBefore() > 0) {
                lineBuilder.append(HudSpace.offset(piece.gapBefore()));
                totalWidth += piece.gapBefore();
            }
            lineBuilder.append(piece.component());
            int width = piece.width();
            boolean inGrid = pieces.size() > 1 && i < columns.size();
            if (inGrid && columns.get(i) > width) {
                lineBuilder.append(HudSpace.offset(columns.get(i) - width));
                width = columns.get(i);
            }
            totalWidth += width;
        }
        return new EncodedLine(lineBuilder.build(), pieces.isEmpty() ? 0 : totalWidth);
    }

    private void collectLines(Box<?> box, List<List<LineItem>> out) {
        if (box == null) {
            return;
        }

        if (box instanceof Flex flex && !flex.isRow()) {
            // Flex column container: each child starts a new line
            for (Box<?> child : flex.children()) {
                List<LineItem> line = new ArrayList<>();
                collectRowItems(child, 0.0, line);
                if (!line.isEmpty()) {
                    out.add(line);
                }
            }
        } else {
            // Single line layout (Row, Text, or single element)
            List<LineItem> line = new ArrayList<>();
            collectRowItems(box, 0.0, line);
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
    }

    private void collectRowItems(Box<?> box, double gapBefore, List<LineItem> out) {
        if (box == null) {
            return;
        }

        if (box instanceof Text text) {
            out.add(new LineItem(text, gapBefore));
        } else if (box instanceof Flex flex && flex.isRow()) {
            double gap = flex.gap();
            List<Box<?>> children = flex.children();
            for (int i = 0; i < children.size(); i++) {
                double currentGap = i == 0 ? gapBefore : gap;
                collectRowItems(children.get(i), currentGap, out);
            }
        } else if (box instanceof Container<?> container) {
            double gap = container.gap();
            List<Box<?>> children = container.children();
            for (int i = 0; i < children.size(); i++) {
                double currentGap = i == 0 ? gapBefore : gap;
                collectRowItems(children.get(i), currentGap, out);
            }
        }
    }

    private record LineItem(Text text, double gapBefore) {}

    private record Piece(Component component, int width, int gapBefore) {}

    private record EncodedLine(Component component, int width) {}

    /** Resolved box geometry in anchor-relative pixels and font Y units. */
    private record Frame(int left, int top, int width, int height, int textTop, int step, int padLeft, int padRight) {
        int lineY(int index) {
            return textTop + index * step;
        }
    }
}
