package dev.horuse.pixelay.content;

import dev.horuse.pixelay.utils.PlaceholderResolver;
import java.util.function.Supplier;

/**
 * Supplies text content for a canvas component. Re-evaluates on each refresh to update dynamic content.
 */
@FunctionalInterface
public interface TextContent {
    String render();

    /** Creates static text content. */
    static TextContent of(String text) {
        return () -> text;
    }

    /** Creates dynamic text content evaluated on each refresh. */
    static TextContent supplier(Supplier<String> supplier) {
        return supplier::get;
    }

    /**
     * Creates dynamic text content evaluated with global placeholders on each refresh.
     *
     * <p><b>Do not pass untrusted player text to this method.</b> PlaceholderAPI expands placeholders
     * and parses results as MiniMessage. Players can inject tags or leak data. Sanitize untrusted input first.
     */
    static TextContent placeholder(String template) {
        return () -> PlaceholderResolver.resolve(template);
    }
}
