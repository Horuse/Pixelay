package dev.horuse.pixelay.screen;

import java.util.UUID;

/** Keeps each player's calibrated screen scale (see {@link ScreenSession}). */
public interface ScreenScaleStore {
    /** @return the saved scale, or {@code null} if the player never calibrated */
    Double load(UUID player);

    void save(UUID player, double scale);
}
