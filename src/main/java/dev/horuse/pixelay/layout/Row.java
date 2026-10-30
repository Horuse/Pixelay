package dev.horuse.pixelay.layout;

/**
 * Horizontal flex container arranging children in a row.
 */
public final class Row extends Flex {

    public Row(LayoutItem... children) {
        super(true, children);
    }

    @Override
    public Row align(Align align) {
        super.align(align);
        return this;
    }

    @Override
    public Row justify(Justify justify) {
        super.justify(justify);
        return this;
    }

    @Override
    public Row gap(double gap) {
        super.gap(gap);
        return this;
    }

    @Override
    public Row wrap(boolean wrap) {
        super.wrap(wrap);
        return this;
    }

    @Override
    public Row rowGap(double rowGap) {
        super.rowGap(rowGap);
        return this;
    }
}
