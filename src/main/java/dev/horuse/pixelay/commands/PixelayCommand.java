package dev.horuse.pixelay.commands;

import dev.horuse.pixelay.PixelayPlugin;
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
                    ? "Layout debug on: every element shows its block (blue), elements that do not fit their space are red."
                    : "Layout debug off."
            );
            return true;
        }

        sender.sendMessage(
            "Pixelay " + plugin.getPluginMeta().getVersion() + " — usage: /" + label + " <reload|debug layout>"
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
            return "layout".startsWith(args[1].toLowerCase()) ? List.of("layout") : List.of();
        }
        return List.of();
    }
}
