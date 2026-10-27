package dev.horuse.pixelay.layout;

/**
 * A vertical flex container (CSS {@code display: flex; flex-direction: column}).
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
