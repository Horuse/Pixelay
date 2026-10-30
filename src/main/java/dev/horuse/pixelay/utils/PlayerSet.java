package dev.horuse.pixelay.utils;

import java.util.Set;
import java.util.UUID;

/** Maintains per-player boolean flags using a set of active player identifiers. */
public final class PlayerSet {

    private PlayerSet() {}

    /** @return True if the flag is enabled for {@code player} after toggling. */
    public static boolean toggle(Set<UUID> on, UUID player) {
        if (on.remove(player)) {
            return false;
        }
        on.add(player);
        return true;
    }
}
