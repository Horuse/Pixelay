package dev.horuse.pixelay.layout;

/**
 * A row or a column (CSS flexbox): children are laid out along the main axis with {@link #gap}.
 * In a row each child starts at its natural width; when they do not fit, the missing space is taken
 * from them by {@code shrink x width} (never below their min-content width, so texts wrap and words
 * stay whole), and free space goes to growing children (or is distributed by {@link #justify}).
 * Each child is placed on the cross axis by {@link #align} or its own {@code alignSelf}; {@code STRETCH}
 * (the default) fills it, except for children with a fixed size on that axis.
 */
public final class Flex extends Container<Flex> {

    private final boolean horizontal;
    private Align align = Align.STRETCH;
    private Justify justify = Justify.START;

    Flex(boolean horizontal, LayoutItem... items) {
        super(items);
        this.horizontal = horizontal;
    }

    /** @return whether this is a row (else a column) */
    public boolean isRow() {
        return horizontal;
    }

    /** Cross-axis alignment of the children (CSS {@code align-items}). */
    public Flex align(Align align) {
        this.align = align;
        return this;
    }

    /** Main-axis distribution when no child grows (CSS {@code justify-content}). */
    public Flex justify(Justify justify) {
        this.justify = justify;
        return this;
    }

    @Override
    protected Size measureContent(double maxWidth) {
        Size[] sizes = childSizes(maxWidth);
        double main = gaps();
        double cross = 0;
        for (Size size : sizes) {
            main += size.main(horizontal);
            cross = Math.max(cross, size.cross(horizontal));
        }
        return Size.of(horizontal, main, cross);
    }

    @Override
    protected double minContentWidth() {
        if (!horizontal) {
            return widestChildMinWidth();
        }
        double width = gaps();
        for (Box<?> child : children) {
            width += child instanceof Spacer spacer ? spacer.mainLength() : child.minOuterWidth();
        }
        return width;
    }

    private double gaps() {
        return gap * Math.max(0, children.size() - 1);
    }

    /** Child size with margin; a spacer only has its length along the main axis. */
    private Size childSize(Box<?> child, double maxWidth) {
        if (child instanceof Spacer spacer) {
            Insets m = spacer.margin;
            double mainMargin = horizontal ? m.horizontal() : m.vertical();
            double crossMargin = horizontal ? m.vertical() : m.horizontal();
            return Size.of(horizontal, spacer.mainLength() + mainMargin, crossMargin);
        }
        return child.outer(maxWidth);
    }

    /**
     * Child sizes (with margins) under a width limit. A column gives every child its own width. A row
     * starts from natural widths, shrinks them when they do not fit and gives growing children what
     * is left; the returned width is the space assigned to the child, the height what it needs there.
     */
    private Size[] childSizes(double maxWidth) {
        Size[] sizes = new Size[children.size()];
        if (!horizontal || maxWidth < 0) {
            for (int i = 0; i < sizes.length; i++) {
                sizes[i] = childSize(children.get(i), maxWidth);
            }
            return sizes;
        }

        // flex-basis: auto — every child starts at its natural width (CSS flex-grow / flex-shrink).
        double[] widths = new double[sizes.length];
        double used = gaps();
        for (int i = 0; i < sizes.length; i++) {
            widths[i] = childSize(children.get(i), Box.UNBOUNDED).width();
            used += widths[i];
        }

        if (used > maxWidth) {
            shrink(widths, used - maxWidth);
        } else {
            double totalGrow = 0;
            for (Box<?> child : children) {
                totalGrow += child.grow;
            }
            double free = maxWidth - used;
            for (int i = 0; i < sizes.length; i++) {
                Box<?> child = children.get(i);
                if (child.grow > 0) {
                    widths[i] += (free * child.grow) / totalGrow;
                }
            }
        }

        for (int i = 0; i < sizes.length; i++) {
            Size size = childSize(children.get(i), widths[i]);
            sizes[i] = new Size(Math.max(widths[i], size.width()), size.height());
        }
        return sizes;
    }

    /** Takes {@code deficit} px from the widths, by shrink x width, never below min-content (CSS flex-shrink). */
    private void shrink(double[] widths, double deficit) {
        boolean[] frozen = new boolean[widths.length];
        for (int round = 0; round < widths.length && deficit > 0.01; round++) {
            double weight = 0;
            for (int i = 0; i < widths.length; i++) {
                Box<?> child = children.get(i);
                if (!frozen[i] && child.shrink > 0 && !child.hasFixedWidth() && !(child instanceof Spacer)) {
                    weight += child.shrink * widths[i];
                }
            }
            if (weight <= 0) {
                return;
            }

            double taken = 0;
            for (int i = 0; i < widths.length; i++) {
                Box<?> child = children.get(i);
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
        Size[] sizes = childSizes(content.width());
        double used = gaps();
        double totalGrow = 0;
        for (int i = 0; i < sizes.length; i++) {
            used += sizes[i].main(horizontal);
            totalGrow += children.get(i).grow;
        }

        // A row already gave growing children their share of the width; a column grows here.
        double free = Math.max(0, (horizontal ? content.width() : content.height()) - used);
        boolean distributeGrow = !horizontal && totalGrow > 0;
        double cursor = 0;
        double between = gap;
        if (totalGrow == 0) {
            switch (justify) {
                case CENTER -> cursor = free / 2;
                case END -> cursor = free;
                case SPACE_BETWEEN -> between += children.size() > 1 ? free / (children.size() - 1) : 0;
                case START -> {
                }
            }
        }

        double crossSpace = horizontal ? content.height() : content.width();
        for (int i = 0; i < sizes.length; i++) {
            Box<?> child = children.get(i);
            Size outer = sizes[i];
            double mainSize = outer.main(horizontal) + (distributeGrow ? (free * child.grow) / totalGrow : 0);
            Align crossAlign = child.alignSelf != null ? child.alignSelf : align;
            boolean fixedCross = horizontal ? child.hasFixedHeight() : child.hasFixedWidth();
            double crossSize =
                crossAlign == Align.STRETCH && !fixedCross ? crossSpace : Math.min(crossSpace, outer.cross(horizontal));
            double crossPos = crossAlign.offset(crossSpace, crossSize);
            Rect slot = horizontal
                ? new Rect(content.x() + cursor, content.y() + crossPos, mainSize, crossSize)
                : new Rect(content.x() + crossPos, content.y() + cursor, crossSize, mainSize);
            child.arrange(slot.inset(child.margin), overflowsHere());
            cursor += mainSize + between;
        }
    }
}
