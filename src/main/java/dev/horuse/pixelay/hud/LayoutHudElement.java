package dev.horuse.pixelay.hud;

import dev.horuse.pixelay.layout.Box;
import dev.horuse.pixelay.layout.Container;
import dev.horuse.pixelay.layout.Flex;
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
 */
public class LayoutHudElement extends BasicHudElement {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Function<Player, LayoutItem> layoutSupplier;
    private final boolean hasPapi;

    /**
     * Creates a HUD element from a static layout tree.
     *
     * @param anchor target screen anchor
     * @param offsetX horizontal pixel offset
     * @param offsetY vertical pixel offset
     * @param root layout item root
     */
    public LayoutHudElement(HudAnchor anchor, int offsetX, int offsetY, LayoutItem root) {
        this(anchor, offsetX, offsetY, player -> root);
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

        TextComponent.Builder builder = Component.text();

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            List<LineItem> items = lines.get(lineIndex);
            if (items.isEmpty()) {
                continue;
            }

            int shaderLine = anchor().isBottomAnchored() ? lines.size() - 1 - lineIndex : lineIndex;
            Component encodedLine = renderLine(items, player, shaderLine);
            builder.append(encodedLine);
        }

        Component content = builder.build();
        if (offsetX() == 0 || Component.empty().equals(content)) {
            return content;
        }

        return Component.text()
            .append(HudSpace.offset(offsetX()))
            .append(content)
            .append(HudSpace.offset(-offsetX()))
            .build();
    }

    private Component renderLine(List<LineItem> items, Player player, int lineIndex) {
        List<Component> encodedPieces = new ArrayList<>();
        List<Integer> gaps = new ArrayList<>();
        int totalWidth = 0;

        for (int i = 0; i < items.size(); i++) {
            LineItem item = items.get(i);
            String rawText = item.text.renderText();
            if (rawText == null || rawText.isEmpty()) {
                continue;
            }

            if (hasPapi) {
                rawText = PlaceholderAPI.setPlaceholders(player, rawText);
            }

            Component parsed = MINI_MESSAGE.deserialize(rawText);
            int[] widthTracker = new int[] { 0 };
            Component encoded = HudCodec.encodeComponent(parsed, anchor(), lineIndex, widthTracker);

            int pieceWidth = widthTracker[0];
            encodedPieces.add(encoded);

            if (!encodedPieces.isEmpty() && encodedPieces.size() > 1) {
                int gapPx = (int) Math.round(item.gapBefore);
                gaps.add(gapPx);
                totalWidth += gapPx;
            }

            totalWidth += pieceWidth;
        }

        if (encodedPieces.isEmpty() || totalWidth <= 0) {
            return Component.empty();
        }

        TextComponent.Builder lineBuilder = Component.text();

        if (anchor().isRightAligned()) {
            lineBuilder.append(HudSpace.offset(-totalWidth));
        } else if (anchor().isCenterAligned()) {
            lineBuilder.append(HudSpace.offset(-(totalWidth / 2)));
        }

        for (int i = 0; i < encodedPieces.size(); i++) {
            if (i > 0 && i - 1 < gaps.size()) {
                int gap = gaps.get(i - 1);
                if (gap > 0) {
                    lineBuilder.append(HudSpace.offset(gap));
                }
            }
            lineBuilder.append(encodedPieces.get(i));
        }

        if (anchor().isLeftAligned()) {
            lineBuilder.append(HudSpace.offset(-totalWidth));
        } else if (anchor().isCenterAligned()) {
            int half = totalWidth / 2;
            int rem = totalWidth - half;
            lineBuilder.append(HudSpace.offset(-rem));
        }

        return lineBuilder.build();
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
}
