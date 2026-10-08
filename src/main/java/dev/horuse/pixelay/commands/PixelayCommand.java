package dev.horuse.pixelay.commands;

import dev.horuse.pixelay.PixelayPlugin;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
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

        sender.sendMessage("Pixelay " + plugin.getPluginMeta().getVersion() + " — usage: /" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        String[] args
    ) {
        return args.length == 1 && "reload".startsWith(args[0].toLowerCase()) ? List.of("reload") : List.of();
    }
}
