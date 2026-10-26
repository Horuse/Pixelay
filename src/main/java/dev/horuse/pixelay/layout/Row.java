package dev.horuse.pixelay.layout;

/**
 * A horizontal flex container (CSS {@code display: flex; flex-direction: row}).
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
}
