package dev.horuse.pixelay.layout;

/**
 * A row or a column (CSS flexbox): children are laid out along the main axis with {@link #gap},
 * free space goes to growing children (or is distributed by {@link #justify}), and each child is
 * placed on the cross axis by {@link #align} or its own {@code alignSelf}.
 */
public final class Flex extends Container {

    private final boolean row;
    private Align align = Align.STRETCH;
    private Justify justify = Justify.START;

    Flex(boolean row, LayoutItem... items) {
        super(items);
        this.row = row;
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
    protected double[] measureContent() {
        double main = 0;
        double cross = 0;
        for (Box child : children) {
            double[] size = outer(child);
            main += main(size);
            cross = Math.max(cross, cross(size));
        }
        main += gap * Math.max(0, children.size() - 1);
        return row ? new double[] { main, cross } : new double[] { cross, main };
    }

    @Override
    protected void arrangeContent(Rect content) {
        double[][] sizes = new double[children.size()][];
        double used = gap * Math.max(0, children.size() - 1);
        double totalGrow = 0;
        for (int i = 0; i < children.size(); i++) {
            sizes[i] = outer(children.get(i));
            used += main(sizes[i]);
            totalGrow += children.get(i).grow;
        }

        double free = Math.max(0, (row ? content.width() : content.height()) - used);
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

        double crossSpace = row ? content.height() : content.width();
        for (int i = 0; i < children.size(); i++) {
            Box child = children.get(i);
            double mainSize = main(sizes[i]) + (totalGrow > 0 ? (free * child.grow) / totalGrow : 0);
            Align crossAlign = child.alignSelf != null ? child.alignSelf : align;
            double crossSize = crossAlign == Align.STRETCH ? crossSpace : Math.min(crossSpace, cross(sizes[i]));
            double crossPos = crossAlign.offset(crossSpace, crossSize);
            placeChild(child, content, cursor, mainSize, crossPos, crossSize);
            cursor += mainSize + between;
        }
    }

    private void placeChild(
        Box child,
        Rect content,
        double mainPos,
        double mainSize,
        double crossPos,
        double crossSize
    ) {
        Insets m = child.margin;
        Rect outer = row
            ? new Rect(content.x() + mainPos, content.y() + crossPos, mainSize, crossSize)
            : new Rect(content.x() + crossPos, content.y() + mainPos, crossSize, mainSize);
        child.arrange(outer.inset(m));
    }

    private double main(double[] size) {
        return row ? size[0] : size[1];
    }

    private double cross(double[] size) {
        return row ? size[1] : size[0];
    }
}
