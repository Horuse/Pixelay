package dev.horuse.pixelay.utils;

import dev.horuse.pixelay.Pixelay;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ids of packet-only entities. One shared source, so client entities never overlap; ids start at
 * {@code packets.fake-entity-id-base}, high enough to never collide with real server entities (which grow
 * from 0).
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
