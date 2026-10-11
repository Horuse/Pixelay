package dev.horuse.pixelay.utils;

import java.util.Set;
import java.util.UUID;

/** Per-player on/off switches kept as a set of players who have them on. */
public final class PlayerSet {

    private PlayerSet() {}

    /** @return whether the switch is now on for {@code player} */
    public static boolean toggle(Set<UUID> on, UUID player) {
        if (on.remove(player)) {
            return false;
        }
        on.add(player);
        return true;
    }
}
