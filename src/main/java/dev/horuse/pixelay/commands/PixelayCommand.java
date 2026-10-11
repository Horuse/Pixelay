package dev.horuse.pixelay.commands;

import dev.horuse.pixelay.PixelayPlugin;
import dev.horuse.pixelay.clicks.CanvasClicks;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.debug.CanvasBoundsDebug;
import dev.horuse.pixelay.debug.LayoutDebug;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

    // Pastel palette: messages are easy to tell apart at a glance.
    private static final String PREFIX = "<#C4B5FD>Pixelay <#94A3B8>» ";
    private static final String TEXT = "<#E2E8F0>";
    private static final String ON = "<#86EFAC>";
    private static final String OFF = "<#FDBA74>";
    private static final String ERROR = "<#FCA5A5>";
    private static final String COMMAND = "<#93C5FD>";
    private static final String MUTED = "<#94A3B8>";

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
                send(sender, ON + "Configuration reloaded" + MUTED + " (config.yml, resource packs, font widths)");
            } catch (ConfigurateException e) {
                send(sender, ERROR + "Reload failed: " + MUTED + e.getMessage());
                plugin.getLogger().warning("Reload failed: " + e.getMessage());
            }
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            if (!(sender instanceof Player player)) {
                send(sender, ERROR + "Debug modes are for players only.");
                return true;
            }
            switch (args[1].toLowerCase()) {
                case "layout" -> layoutDebug(player);
                case "bounds" -> toggle(
                    player,
                    "Canvas bounds",
                    CanvasBoundsDebug.toggle(player),
                    "outlines of the canvases near you"
                );
                case "layers" -> {
                    boolean on = Layers.toggleExploded(player);
                    // Click tiles stand on the text layer, which just moved.
                    CanvasClicks.respawnAll();
                    toggle(
                        player,
                        "Layers apart",
                        on,
                        Layers.EXPLODED_STEP + " blocks between layers on your private canvases; look from the side"
                    );
                }
                default -> help(sender, label);
            }
            return true;
        }

        help(sender, label);
        return true;
    }

    private void layoutDebug(Player player) {
        boolean on = LayoutDebug.toggle(player);
        toggle(player, "Layout debug", on, null);
        if (on) {
            send(
                player,
                MUTED + "Text: <#93C5FD>fits" + MUTED + ", <#FCA5A5>overflows" + MUTED + ", <#FDBA74>inside an overflow"
            );
            send(
                player,
                MUTED +
                    "Panels: <#7DD3FC>row" +
                    MUTED +
                    ", <#BEF264>column" +
                    MUTED +
                    ", <#D8B4FE>grid" +
                    MUTED +
                    ", <#CBD5E1>stack" +
                    MUTED +
                    ", <#F8FAFC>spacer" +
                    MUTED +
                    ", <#FDE68A>click zone"
            );
        }
    }

    private static void toggle(CommandSender sender, String name, boolean on, String details) {
        String state = on ? ON + "on" : OFF + "off";
        send(sender, TEXT + name + " " + state + (on && details != null ? MUTED + " — " + details : ""));
    }

    private void help(CommandSender sender, String label) {
        send(sender, TEXT + "Version " + COMMAND + plugin.getPluginMeta().getVersion());
        line(sender, label, "reload", "reload config.yml, resource packs and font widths");
        line(sender, label, "debug layout", "show layout boxes and overflowing texts");
        line(sender, label, "debug bounds", "outline canvases near you");
        line(sender, label, "debug layers", "pull layers apart on your private canvases");
    }

    private static void line(CommandSender sender, String label, String args, String description) {
        sender.sendMessage(
            MiniMessage.miniMessage().deserialize(
                "  " + COMMAND + "/" + label + " " + args + MUTED + " — " + description
            )
        );
    }

    private static void send(CommandSender sender, String miniMessage) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(PREFIX + miniMessage));
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        String[] args
    ) {
        if (args.length == 1) {
            return startingWith(args[0], "reload", "debug");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return startingWith(args[1], "layout", "bounds", "layers");
        }
        return List.of();
    }

    private static List<String> startingWith(String typed, String... options) {
        String prefix = typed.toLowerCase();
        return Arrays.stream(options)
            .filter(option -> option.startsWith(prefix))
            .toList();
    }
}
