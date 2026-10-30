package dev.horuse.pixelay.utils;

import dev.horuse.pixelay.Pixelay;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drops clicks from a player that occur within debounce interval.
 * Prevents duplicate actions when mouse clicks produce multiple network packets.
 */
public final class ClickDebounce {

    private final Map<UUID, Long> lastAccepted = new ConcurrentHashMap<>();

    /** @return True if the click is accepted, or false if received too quickly. */
    public boolean accept(UUID player) {
        long now = System.currentTimeMillis();
        Long last = lastAccepted.get(player);
        if (last != null && now - last < Pixelay.settings().clicks().debounceMs()) {
            return false;
        }
        lastAccepted.put(player, now);
        return true;
    }

    public void forget(UUID player) {
        lastAccepted.remove(player);
    }

    public void clear() {
        lastAccepted.clear();
    }
}
