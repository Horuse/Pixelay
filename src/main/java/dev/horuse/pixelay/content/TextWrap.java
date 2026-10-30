package dev.horuse.pixelay.content;

/**
 * Controls text wrapping behavior similar to CSS.
 * Pixelay calculates line breaks directly so rendered text matches measured bounds.
 *
 * @param whiteSpace CSS {@code white-space} setting
 * @param wordBreak CSS {@code word-break} setting
 */
public record TextWrap(WhiteSpace whiteSpace, WordBreak wordBreak) {
    /** Wraps lines at spaces and hyphens without breaking words. */
    public static final TextWrap NORMAL = new TextWrap(WhiteSpace.NORMAL, WordBreak.NORMAL);

    /** Controls line wrapping behavior. Preserves explicit newlines. */
    public enum WhiteSpace {
        /** Lines wrap to the available width. */
        NORMAL,
        /** Disables line wrapping. Long lines overflow the container. */
        NOWRAP,
    }

    /** Controls word break locations. */
    public enum WordBreak {
        /** Words do not break. Words wider than the line overflow ({@code word-break: normal}). */
        NORMAL,
        /** Breaks words only when a word exceeds full line width ({@code overflow-wrap: break-word}). */
        BREAK_WORD,
        /** Breaks text between any two characters ({@code word-break: break-all}). */
        BREAK_ALL,
    }

    public TextWrap withWhiteSpace(WhiteSpace value) {
        return new TextWrap(value, wordBreak);
    }

    public TextWrap withWordBreak(WordBreak value) {
        return new TextWrap(whiteSpace, value);
    }
}
