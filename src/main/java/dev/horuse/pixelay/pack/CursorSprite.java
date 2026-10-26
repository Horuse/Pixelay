package dev.horuse.pixelay.pack;

/**
 * A sprite of the screen cursor ({@code generated-pack.cursor.sprites} in config.yml), drawn from
 * {@code cursors/<name>.png} of the pack.
 *
 * @param name what boxes ask for with {@code cursor("name")}, like CSS {@code cursor}
 * @param hotSpotX the pointing point, in sprite px from the left (CSS {@code cursor: url(...) x y})
 * @param hotSpotY the pointing point, in sprite px from the top
 */
public record CursorSprite(String name, double hotSpotX, double hotSpotY) {}
