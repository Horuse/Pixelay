package dev.horuse.pixelay.pack;

/**
 * A sprite of the screen cursor ({@code generated-pack.cursor.sprites} in config.yml).
 * Drawn from {@code cursors/<name>.png} in the pack.
 *
 * @param name The name that boxes request with {@code cursor("name")}.
 * @param hotSpotX The active point in sprite pixels from the left edge.
 * @param hotSpotY The active point in sprite pixels from the top edge.
 */
public record CursorSprite(String name, double hotSpotX, double hotSpotY) {}
