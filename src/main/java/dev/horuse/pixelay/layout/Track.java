package dev.horuse.pixelay.layout;

/** Defines track size in pixels, fractional units, or automatic sizing. */
public record Track(Kind kind, double value) {
    public enum Kind {
        PX,
        FR,
        AUTO,
    }

    static final Track AUTO = new Track(Kind.AUTO, 0);
}
