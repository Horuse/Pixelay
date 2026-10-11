package dev.horuse.pixelay.utils;

import dev.horuse.pixelay.Pixelay;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drops clicks of a player that come within {@code clicks.debounce-ms} of the last accepted one: one
 * mouse click often arrives as two packets.
 */
public final class ClickDebounce {

    private final Map<UUID, Long> lastAccepted = new ConcurrentHashMap<>();

    /** @return whether the click is accepted (and remembered), {@code false} when it comes too soon */
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
