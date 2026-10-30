package dev.horuse.pixelay.layout;

/**
 * Represents empty space along the main axis of a container.
 */
public final class Spacer extends Box<Spacer> {

    private final double length;

    /** Creates a flexible spacer that expands to fill remaining space. */
    public Spacer() {
        this(-1);
    }

    /** Creates a fixed spacer with the specified pixel length. */
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

    /** Returns raw length with negative values indicating flexible space. */
    public double length() {
        return length;
    }

    /** Returns non-negative length along the main axis. */
    double mainLength() {
        return Math.max(0, length);
    }
}
