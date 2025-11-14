package dev.oakheart.oaktools.commands;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.commands.subcommands.*;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main command executor for /oaktools command.
 */
public class OakToolsCommand implements CommandExecutor, TabCompleter {

    private final OakTools plugin;
    private final GiveCommand giveCommand;
    private final ReloadCommand reloadCommand;
    private final InfoCommand infoCommand;
    private final RepairCommand repairCommand;

    public OakToolsCommand(OakTools plugin) {
        this.plugin = plugin;
        this.giveCommand = new GiveCommand(plugin);
        this.reloadCommand = new ReloadCommand(plugin);
        this.infoCommand = new InfoCommand(plugin);
        this.repairCommand = new RepairCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "give" -> {
                return giveCommand.execute(sender, args);
            }
            case "reload" -> {
                return reloadCommand.execute(sender, args);
            }
            case "info" -> {
                return infoCommand.execute(sender, args);
            }
            case "repair" -> {
                return repairCommand.execute(sender, args);
            }
            default -> {
                plugin.getMessageService().sendCommandMessage(sender, "unknown_subcommand");
                return true;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Subcommands
            completions.addAll(Arrays.asList("give", "reload", "info", "repair"));
            return filterCompletions(completions, args[0]);
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();

            // Player names for give, info, repair
            if (subcommand.equals("give") || subcommand.equals("info") || subcommand.equals("repair")) {
                return null; // Return null to show online players
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Tool types for give command
            for (ToolType type : ToolType.values()) {
                completions.add(type.name().toLowerCase());
            }
            return filterCompletions(completions, args[2]);
        }

        return completions;
    }

    private void sendUsage(CommandSender sender) {
        plugin.getMessageService().sendCommandMessage(sender, "help.header");
        plugin.getMessageService().sendCommandMessage(sender, "help.give");
        plugin.getMessageService().sendCommandMessage(sender, "help.reload");
        plugin.getMessageService().sendCommandMessage(sender, "help.info");
        plugin.getMessageService().sendCommandMessage(sender, "help.repair");
    }

    private List<String> filterCompletions(List<String> completions, String partial) {
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                .toList();
    }
}
