package dev.horuse.pixelay.screen;

import dev.horuse.pixelay.Pixelay;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;

/**
 * The default {@link ScreenScaleStore}: {@code plugins/Pixelay/screen-scales.yml}, one line per player.
 * Reads once, writes asynchronously on every save.
 */
public final class YamlScaleStore implements ScreenScaleStore {

    private static final String FILE = "screen-scales.yml";
    private static YamlScaleStore shared;

    private final Map<UUID, Double> scales = new ConcurrentHashMap<>();
    private final Path file;
    /** The newest file content waiting to be written. */
    private final AtomicReference<String> latest = new AtomicReference<>();

    private YamlScaleStore(Path file) {
        this.file = file;
        read();
    }

    static synchronized YamlScaleStore shared() {
        if (shared == null) {
            shared = new YamlScaleStore(Pixelay.plugin().getDataFolder().toPath().resolve(FILE));
        }
        return shared;
    }

    @Override
    public Double load(UUID player) {
        return scales.get(player);
    }

    @Override
    public void save(UUID player, double scale) {
        scales.put(player, scale);
        latest.set(render());
        Bukkit.getScheduler().runTaskAsynchronously(Pixelay.plugin(), this::writeLatest);
    }

    private void read() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int colon = line.indexOf(':');
                if (line.isBlank() || line.startsWith("#") || colon < 0) {
                    continue;
                }
                try {
                    scales.put(
                        UUID.fromString(line.substring(0, colon).trim().replace("\"", "")),
                        Double.parseDouble(line.substring(colon + 1).trim())
                    );
                } catch (IllegalArgumentException ignored) {
                    // A broken line only loses that player's scale.
                }
            }
        } catch (IOException e) {
            Pixelay.plugin()
                .getLogger()
                .warning("Could not read " + FILE + ": " + e.getMessage());
        }
    }

    private String render() {
        StringBuilder out = new StringBuilder("# Calibrated screen scales of ScreenSession, per player UUID.\n");
        scales.forEach((id, scale) ->
            out
                .append('"')
                .append(id)
                .append("\": ")
                .append(String.format(Locale.ROOT, "%.3f", scale))
                .append('\n')
        );
        return out.toString();
    }

    /**
     * Writes the newest snapshot. Taking it and writing it happen under one lock, so a write that started
     * later can never put an older snapshot on disk after a newer one.
     */
    private synchronized void writeLatest() {
        String content = latest.getAndSet(null);
        if (content != null) {
            write(content);
        }
    }

    private void write(String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Pixelay.plugin()
                .getLogger()
                .warning("Could not write " + FILE + ": " + e.getMessage());
        }
    }
}
