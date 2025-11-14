package dev.oakheart.oaktools.commands.subcommands;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * /oaktools repair [player]
 */
public class RepairCommand {

    private final OakTools plugin;

    public RepairCommand(OakTools plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("oaktools.repair")) {
            plugin.getMessageService().sendCommandMessage(sender, "no_permission");
            return true;
        }

        Player target;

        if (args.length >= 2) {
            // Repair specific player's tool
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                plugin.getMessageService().sendCommandMessage(sender, "repair.player_not_found",
                        Map.of("player", args[1]));
                return true;
            }
        } else {
            // Repair self (must be player)
            if (!(sender instanceof Player player)) {
                plugin.getMessageService().sendCommandMessage(sender, "repair.must_specify_player");
                return true;
            }
            target = player;
        }

        ItemStack item = target.getInventory().getItemInMainHand();

        if (!plugin.getItemFactory().isTool(item)) {
            plugin.getMessageService().sendCommandMessage(sender, "repair.not_holding_tool",
                    Map.of("player", target.getName()));
            return true;
        }

        plugin.getDurabilityService().repairFully(item);
        plugin.getDisplayService().updateDisplay(item);

        plugin.getMessageService().sendCommandMessage(sender, "repair.success_sender",
                Map.of("player", target.getName()));
        if (target != sender) {
            plugin.getMessageService().sendCommandMessage(target, "repair.success_target");
        }

        return true;
    }
}
