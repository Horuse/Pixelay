package dev.horuse.pixelay.layout;

/** A grid track size: fixed px, a fraction of the free space ({@code fr}) or {@code auto} (content). */
public record Track(Kind kind, double value) {
    public enum Kind {
        PX,
        FR,
        AUTO,
    }

    static final Track AUTO = new Track(Kind.AUTO, 0);
}
