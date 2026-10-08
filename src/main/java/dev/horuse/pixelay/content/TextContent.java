package dev.horuse.pixelay.content;

import dev.horuse.pixelay.utils.PlaceholderResolver;
import java.util.function.Supplier;

/**
 * Source of the text rendered by a canvas component. Re-evaluated on every refresh, so dynamic
 * content (suppliers, placeholders) updates live.
 */
@FunctionalInterface
public interface TextContent {
    String render();

    /** Constant text. */
    static TextContent of(String text) {
        return () -> text;
    }

    /** Text recomputed from a supplier on every refresh. */
    static TextContent supplier(Supplier<String> supplier) {
        return supplier::get;
    }

    /** Template resolved through global (player-agnostic) placeholders on every refresh. */
    static TextContent placeholder(String template) {
        return () -> PlaceholderResolver.resolve(template);
    }
}
