package dev.horuse.pixelay.utils;

import dev.horuse.pixelay.Pixelay;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates unique identifiers for packet-only entities.
 * Identifiers start at {@code packets.fake-entity-id-base} to avoid collisions with server entities.
 */
public final class FakeEntityIds {

    private static final AtomicInteger NEXT = new AtomicInteger();
    private static volatile boolean baseApplied;

    private FakeEntityIds() {}

    public static int next() {
        if (!baseApplied) {
            synchronized (NEXT) {
                if (!baseApplied) {
                    NEXT.set(Pixelay.settings().packets().fakeEntityIdBase());
                    baseApplied = true;
                }
            }
        }
        return NEXT.getAndIncrement();
    }
}
