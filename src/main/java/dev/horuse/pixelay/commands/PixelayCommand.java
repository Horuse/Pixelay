package dev.horuse.pixelay.commands;

import dev.horuse.pixelay.PixelayPlugin;
import dev.horuse.pixelay.clicks.CanvasClicks;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.debug.CanvasBoundsDebug;
import dev.horuse.pixelay.debug.LayoutDebug;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurateException;

/** {@code /pixelay reload} — re-reads config.yml and font-widths.yml. */
public class PixelayCommand implements TabExecutor {

    private final PixelayPlugin plugin;

    public PixelayCommand(PixelayPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        String[] args
    ) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            try {
                plugin.reload();
                sender.sendMessage("Pixelay configuration reloaded.");
            } catch (ConfigurateException e) {
                sender.sendMessage("Pixelay reload failed: " + e.getMessage());
                plugin.getLogger().warning("Reload failed: " + e.getMessage());
            }

            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("layout")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can debug layouts.");
                return true;
            }
            boolean enabled = LayoutDebug.toggle(player);
            sender.sendMessage(
                enabled
                    ? "Layout debug on. Text: blue fits, red overflows, orange inside an overflow. Panels: row light blue," +
                          " column lime, grid purple, stack gray, spacer white, click zone yellow."
                    : "Layout debug off."
            );
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("layers")) {
            boolean exploded = Layers.toggleExploded();
            // Click tiles stand on the text layer, which just moved.
            CanvasClicks.respawnAll();
            sender.sendMessage(
                exploded
                    ? "Layers pulled apart (step " +
                          Layers.EXPLODED_STEP +
                          " blocks) for everyone; look at a canvas from the side. Run again to restore."
                    : "Layers restored."
            );
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("bounds")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can see canvas bounds.");
                return true;
            }
            boolean enabled = CanvasBoundsDebug.toggle(player);
            sender.sendMessage(enabled ? "Canvas bounds on: outlines of the canvases near you." : "Canvas bounds off.");
            return true;
        }

        sender.sendMessage(
            "Pixelay " +
                plugin.getPluginMeta().getVersion() +
                " — usage: /" +
                label +
                " <reload|debug layout|debug bounds|debug layers>"
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        String[] args
    ) {
        if (args.length == 1) {
            return List.of("reload", "debug")
                .stream()
                .filter(option -> option.startsWith(args[0].toLowerCase()))
                .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return List.of("layout", "bounds", "layers")
                .stream()
                .filter(option -> option.startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
