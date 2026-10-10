package dev.horuse.pixelay.content;

/**
 * How a text wraps, as in CSS. Pixelay breaks the lines itself and the client only draws them, so what is
 * measured is exactly what is shown.
 *
 * @param whiteSpace CSS {@code white-space}
 * @param wordBreak CSS {@code word-break} / {@code overflow-wrap}
 */
public record TextWrap(WhiteSpace whiteSpace, WordBreak wordBreak) {
    /** Wrap at spaces and after hyphens, never break a word (the CSS default). */
    public static final TextWrap NORMAL = new TextWrap(WhiteSpace.NORMAL, WordBreak.NORMAL);

    /** CSS {@code white-space}. Explicit line breaks ({@code \n}, {@code <br>}) are always kept. */
    public enum WhiteSpace {
        /** Lines wrap to the available width. */
        NORMAL,
        /** Lines never wrap; a long line overflows. */
        NOWRAP,
    }

    /** Where a line may break besides spaces and hyphens. */
    public enum WordBreak {
        /** Nowhere: a word wider than the line overflows ({@code word-break: normal}). */
        NORMAL,
        /** Inside a word only when it does not fit on a line of its own ({@code overflow-wrap: break-word}). */
        BREAK_WORD,
        /** Between any two characters ({@code word-break: break-all}). */
        BREAK_ALL,
    }

    public TextWrap withWhiteSpace(WhiteSpace value) {
        return new TextWrap(value, wordBreak);
    }

    public TextWrap withWordBreak(WordBreak value) {
        return new TextWrap(whiteSpace, value);
    }
}
