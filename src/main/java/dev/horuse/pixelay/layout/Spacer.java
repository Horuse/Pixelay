package dev.horuse.pixelay.layout;

/**
 * Empty space along the main axis of a row or a column: {@code spacer()} takes all free space,
 * {@code space(px)} a fixed length. It never adds size on the cross axis, and outside a row or a
 * column it is empty.
 */
public final class Spacer extends Box<Spacer> {

    private final double length;

    /** Creates a flexible spacer that takes all free space along the parent's main axis (CSS {@code flex: 1}). */
    public Spacer() {
        this(-1);
    }

    /** Creates a fixed spacer of {@code length} px along the parent's main axis. */
    public Spacer(double length) {
        this.length = length;
        if (length < 0) {
            grow = 1;
        }
        shrink = 0;
    }

    @Override
    public String tagName() {
        return "spacer";
    }

    /** Raw length (-1 for flexible spacer, >= 0 for fixed space in px). */
    public double length() {
        return length;
    }

    /** Fixed length along the parent row/column main axis (0 for a flexible spacer). */
    double mainLength() {
        return Math.max(0, length);
    }
}
