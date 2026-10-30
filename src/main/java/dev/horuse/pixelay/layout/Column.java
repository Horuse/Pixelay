package dev.horuse.pixelay.layout;

/**
 * Vertical flex container arranging children in a column.
 */
public final class Column extends Flex {

    public Column(LayoutItem... children) {
        super(false, children);
    }

    @Override
    public Column align(Align align) {
        super.align(align);
        return this;
    }

    @Override
    public Column justify(Justify justify) {
        super.justify(justify);
        return this;
    }

    @Override
    public Column gap(double gap) {
        super.gap(gap);
        return this;
    }
}
