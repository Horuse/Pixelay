package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * Arranges child elements in a row or column following CSS flexbox rules.
 * Main-axis spacing uses {@link #gap}.
 * Cross-axis alignment uses {@link #align}.
 * Items wrap to additional lines when {@link #wrap(boolean)} is enabled.
 */
public class Flex extends Container<Flex> {

    private final boolean horizontal;
    private Align align = Align.STRETCH;
    private Justify justify = Justify.START;
    private boolean wrap;
    private double rowGap = -1;

    public Flex(boolean horizontal, LayoutItem... items) {
        super(items);
        this.horizontal = horizontal;
    }

    public Flex(LayoutItem... items) {
        this(false, items);
    }

    /** @return true if this container is a row, false if a column */
    public boolean isRow() {
        return horizontal;
    }

    @Override
    public String tagName() {
        return horizontal ? "row" : "column";
    }

    /** Sets cross-axis alignment for child elements. */
    public Flex align(Align align) {
        this.align = align;
        return this;
    }

    public Align align() {
        return align;
    }

    /** Sets main-axis alignment distribution. */
    public Flex justify(Justify justify) {
        this.justify = justify;
        return this;
    }

    public Justify justify() {
        return justify;
    }

    /** Enables or disables wrapping onto multiple flex lines. */
    public Flex wrap(boolean wrap) {
        this.wrap = wrap;
        return this;
    }

    public boolean isWrap() {
        return wrap;
    }

    /** Sets cross-axis gap between wrapped lines. */
    public Flex rowGap(double rowGap) {
        this.rowGap = rowGap;
        return this;
    }

    public double rowGap() {
        return rowGap >= 0 ? rowGap : gap;
    }

    @Override
    protected Size measureContent(double maxWidth) {
        List<Box<?>> flow = flowChildren();
        if (horizontal && wrap && maxWidth >= 0 && !flow.isEmpty()) {
            List<List<Box<?>>> lines = buildWrappedLines(flow, maxWidth);
            double maxLineW = 0;
            double totalCrossH = 0;
            double crossGap = rowGap();
            for (int l = 0; l < lines.size(); l++) {
                List<Box<?>> line = lines.get(l);
                double lineW = gaps(line.size());
                double lineH = 0;
                for (Box<?> child : line) {
                    Size s = childSize(child, maxWidth);
                    lineW += s.width();
                    lineH = Math.max(lineH, s.height());
                }
                maxLineW = Math.max(maxLineW, lineW);
                totalCrossH += lineH + (l > 0 ? crossGap : 0);
            }
            return new Size(Math.min(maxWidth, maxLineW), totalCrossH);
        }
        Size[] sizes = childSizes(flow, maxWidth);
        double main = gaps(flow.size());
        double cross = 0;
        for (Size size : sizes) {
            main += size.main(horizontal);
            cross = Math.max(cross, size.cross(horizontal));
        }
        return Size.of(horizontal, main, cross);
    }

    @Override
    protected double minContentWidth() {
        if (!horizontal || wrap) {
            return widestChildMinWidth();
        }
        List<Box<?>> flow = flowChildren();
        double width = gaps(flow.size());
        for (Box<?> child : flow) {
            width += child instanceof Spacer spacer ? spacer.mainLength() : child.minOuterWidth();
        }
        return width;
    }

    private List<List<Box<?>>> buildWrappedLines(List<Box<?>> flow, double maxWidth) {
        List<List<Box<?>>> lines = new ArrayList<>();
        List<Box<?>> current = new ArrayList<>();
        double currentW = 0;
        for (Box<?> child : flow) {
            double childW = childSize(child, maxWidth).width();
            double needed = current.isEmpty() ? childW : currentW + gap + childW;
            if (!current.isEmpty() && needed > maxWidth) {
                lines.add(current);
                current = new ArrayList<>();
                current.add(child);
                currentW = childW;
            } else {
                current.add(child);
                currentW = needed;
            }
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        return lines;
    }

    private double gaps(int count) {
        return gap * Math.max(0, count - 1);
    }

    /** Calculates child dimensions including margin. */
    private Size childSize(Box<?> child, double maxWidth) {
        if (child instanceof Spacer spacer) {
            Insets m = spacer.effectiveMargin();
            double mainMargin = horizontal ? m.horizontal() : m.vertical();
            double crossMargin = horizontal ? m.vertical() : m.horizontal();
            return Size.of(horizontal, spacer.mainLength() + mainMargin, crossMargin);
        }
        return child.outer(maxWidth);
    }

    /** Calculates allocated dimensions for children within available width. */
    private Size[] childSizes(List<Box<?>> flow, double maxWidth) {
        Size[] sizes = new Size[flow.size()];
        if (!horizontal || maxWidth < 0) {
            for (int i = 0; i < sizes.length; i++) {
                sizes[i] = childSize(flow.get(i), maxWidth);
            }
            return sizes;
        }

        // Start each child at its natural width before distributing flex factors.
        double[] widths = new double[sizes.length];
        double used = gaps(flow.size());
        for (int i = 0; i < sizes.length; i++) {
            widths[i] = childSize(flow.get(i), Box.UNBOUNDED).width();
            used += widths[i];
        }

        if (used > maxWidth) {
            shrink(flow, widths, used - maxWidth);
        } else {
            double totalGrow = 0;
            for (Box<?> child : flow) {
                totalGrow += child.grow;
            }
            double free = maxWidth - used;
            for (int i = 0; i < sizes.length; i++) {
                Box<?> child = flow.get(i);
                if (child.grow > 0) {
                    widths[i] += (free * child.grow) / totalGrow;
                }
            }
        }

        for (int i = 0; i < sizes.length; i++) {
            Size size = childSize(flow.get(i), widths[i]);
            sizes[i] = new Size(Math.max(widths[i], size.width()), size.height());
        }
        return sizes;
    }

    /** Reduces child widths by shrink factor while respecting min-content limits. */
    private void shrink(List<Box<?>> flow, double[] widths, double deficit) {
        boolean[] frozen = new boolean[widths.length];
        for (int round = 0; round < widths.length && deficit > 0.01; round++) {
            double weight = 0;
            for (int i = 0; i < widths.length; i++) {
                Box<?> child = flow.get(i);
                if (!frozen[i] && child.shrink > 0 && !child.hasFixedWidth() && !(child instanceof Spacer)) {
                    weight += child.shrink * widths[i];
                }
            }
            if (weight <= 0) {
                return;
            }

            double taken = 0;
            for (int i = 0; i < widths.length; i++) {
                Box<?> child = flow.get(i);
                if (frozen[i] || child.shrink <= 0 || child.hasFixedWidth() || child instanceof Spacer) {
                    continue;
                }
                double target = widths[i] - (deficit * child.shrink * widths[i]) / weight;
                double floor = child.minOuterWidth();
                if (target <= floor) {
                    target = floor;
                    frozen[i] = true;
                }
                taken += widths[i] - target;
                widths[i] = target;
            }
            deficit -= taken;
        }
    }

    @Override
    protected void arrangeContent(Rect content) {
        double sx = visualScaleX();
        double sy = visualScaleY();
        Rect unscaled = new Rect(
            content.x(),
            content.y(),
            sx > 1e-4 ? content.width() / sx : content.width(),
            sy > 1e-4 ? content.height() / sy : content.height()
        );
        List<Box<?>> flow = flowChildren();
        if (horizontal && wrap && !flow.isEmpty()) {
            List<List<Box<?>>> lines = buildWrappedLines(flow, unscaled.width());
            double crossCursor = 0;
            double crossGap = rowGap();
            for (List<Box<?>> line : lines) {
                double lineH = 0;
                for (Box<?> child : line) {
                    lineH = Math.max(lineH, childSize(child, unscaled.width()).height());
                }
                arrangeLine(
                    line,
                    new Rect(unscaled.x(), unscaled.y() + crossCursor, unscaled.width(), lineH),
                    content.x(),
                    content.y(),
                    sx,
                    sy
                );
                crossCursor += lineH + crossGap;
            }
            return;
        }
        arrangeLine(flow, unscaled, content.x(), content.y(), sx, sy);
    }

    private void arrangeLine(List<Box<?>> flow, Rect content, double originX, double originY, double sx, double sy) {
        Size[] sizes = childSizes(flow, content.width());
        double used = gaps(flow.size());
        double totalGrow = 0;
        for (int i = 0; i < sizes.length; i++) {
            Box<?> child = flow.get(i);
            if (!horizontal && child.heightPercent >= 0 && child.fixedHeight < 0) {
                double pctH =
                    Math.max(0, content.height() - child.effectiveMargin().vertical()) * child.heightPercent +
                    child.effectiveMargin().vertical();
                sizes[i] = new Size(sizes[i].width(), pctH);
            }
            used += sizes[i].main(horizontal);
            totalGrow += child.grow;
        }

        // Distribute remaining height to growing children in a column.
        double free = Math.max(0, (horizontal ? content.width() : content.height()) - used);
        boolean distributeGrow = !horizontal && totalGrow > 0;
        double cursor = 0;
        double between = gap;
        if (totalGrow == 0 && !flow.isEmpty()) {
            switch (justify) {
                case CENTER -> cursor = free / 2;
                case END -> cursor = free;
                case SPACE_BETWEEN -> between += flow.size() > 1 ? free / (flow.size() - 1) : 0;
                case SPACE_AROUND -> {
                    double unit = free / flow.size();
                    cursor = unit / 2;
                    between += unit;
                }
                case SPACE_EVENLY -> {
                    double unit = free / (flow.size() + 1);
                    cursor = unit;
                    between += unit;
                }
                case START -> {
                }
            }
        }

        double crossSpace = horizontal ? content.height() : content.width();
        for (int i = 0; i < sizes.length; i++) {
            Box<?> child = flow.get(i);
            Size outer = sizes[i];
            double mainSize = outer.main(horizontal) + (distributeGrow ? (free * child.grow) / totalGrow : 0);
            Align crossAlign = child.alignSelf != null ? child.alignSelf : align;
            boolean fixedCross = horizontal ? child.hasFixedHeight() : child.hasFixedWidth();
            double crossSize;
            if (horizontal && child.heightPercent >= 0 && child.fixedHeight < 0) {
                crossSize = crossSpace * child.heightPercent;
            } else {
                crossSize =
                    crossAlign == Align.STRETCH && !fixedCross
                        ? crossSpace
                        : Math.min(crossSpace, outer.cross(horizontal));
            }
            double crossPos = crossAlign.offset(crossSpace, crossSize);
            Rect slot = horizontal
                ? new Rect(content.x() + cursor, content.y() + crossPos, mainSize, crossSize)
                : new Rect(content.x() + crossPos, content.y() + cursor, crossSize, mainSize);
            Rect unscaledChild = child.effectiveMargin().shrink(slot);
            Rect scaledChild = new Rect(
                originX + (unscaledChild.x() - originX) * sx,
                originY + (unscaledChild.y() - originY) * sy,
                unscaledChild.width() * sx,
                unscaledChild.height() * sy
            );
            arrangeChild(child, scaledChild, overflowsHere());
            cursor += mainSize + between;
        }
    }
}
