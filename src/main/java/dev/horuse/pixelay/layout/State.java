package dev.horuse.pixelay.layout;

/**
 * Built-in states of a box, like CSS pseudo-classes. Give them a look with {@link Box#state(State,
 * java.util.function.Consumer)}; custom states go by name ({@link Box#state(String, boolean)}). When several
 * apply, the more important wins, in this order: {@code DISABLED}, {@code HOVERED}, {@code SELECTED}, then
 * custom states in the order their styles were declared.
 */
public enum State {
    /** Not usable: clicks on it are swallowed, nothing runs (CSS {@code :disabled}). */
    DISABLED,
    /** The pointer is over the box or a box inside it (CSS {@code :hover}). */
    HOVERED,
    /** The chosen one among several (CSS {@code :checked}). */
    SELECTED,
}
