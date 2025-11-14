package dev.oakheart.oaktools.commands.subcommands;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * /oaktools give <player> <tool> [durability]
 */
public class GiveCommand {

    private final OakTools plugin;

    public GiveCommand(OakTools plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("oaktools.give")) {
            plugin.getMessageService().sendCommandMessage(sender, "no_permission");
            return true;
        }

        if (args.length < 3) {
            plugin.getMessageService().sendCommandMessage(sender, "give.usage");
            return true;
        }

        // Get player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.getMessageService().sendCommandMessage(sender, "give.player_not_found",
                    Map.of("player", args[1]));
            return true;
        }

        // Get tool type
        ToolType toolType;
        try {
            toolType = ToolType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getMessageService().sendCommandMessage(sender, "give.invalid_tool");
            return true;
        }

        // Get durability (optional)
        int durability = 0; // 0 = full
        if (args.length >= 4) {
            try {
                durability = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                plugin.getMessageService().sendCommandMessage(sender, "give.invalid_durability",
                        Map.of("value", args[3]));
                return true;
            }
        }

        // Create and give tool
        ItemStack tool = plugin.getItemFactory().createTool(toolType, durability);
        target.getInventory().addItem(tool);

        plugin.getMessageService().sendCommandMessage(sender, "give.success_sender",
                Map.of("tool", toolType.getDisplayName(), "player", target.getName()));
        plugin.getMessageService().sendCommandMessage(target, "give.success_target",
                Map.of("tool", toolType.getDisplayName()));

        return true;
    }
}
