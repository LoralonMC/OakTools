package dev.oakheart.oaktools.commands.subcommands;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.FeedSource;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.Constants;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * /oaktools info [player]
 */
public class InfoCommand {

    private final OakTools plugin;

    public InfoCommand(OakTools plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("oaktools.info")) {
            plugin.getMessageService().sendCommandMessage(sender, "no_permission");
            return true;
        }

        Player target;

        if (args.length >= 2) {
            // Check specific player
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                plugin.getMessageService().sendCommandMessage(sender, "info.player_not_found",
                        Map.of("player", args[1]));
                return true;
            }
        } else {
            // Check self (must be player)
            if (!(sender instanceof Player player)) {
                plugin.getMessageService().sendCommandMessage(sender, "info.must_specify_player");
                return true;
            }
            target = player;
        }

        ItemStack item = target.getInventory().getItemInMainHand();

        if (!plugin.getItemFactory().isTool(item)) {
            plugin.getMessageService().sendCommandMessage(sender, "info.not_holding_tool",
                    Map.of("player", target.getName()));
            return true;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        int currentDamage = plugin.getDurabilityService().getCurrentDamage(item);
        int maxDurability = plugin.getDurabilityService().getMaxDurability(item);
        int remaining = maxDurability - currentDamage;

        plugin.getMessageService().sendCommandMessage(sender, "info.header");
        plugin.getMessageService().sendCommandMessage(sender, "info.player",
                Map.of("player", target.getName()));
        plugin.getMessageService().sendCommandMessage(sender, "info.tool_type",
                Map.of("tool", toolType != null ? toolType.getDisplayName() : "Unknown"));
        plugin.getMessageService().sendCommandMessage(sender, "info.durability",
                Map.of("remaining", String.valueOf(remaining), "max", String.valueOf(maxDurability)));

        // Show feed source for Trowel
        if (toolType == ToolType.TROWEL) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String feedSourceString = meta.getPersistentDataContainer()
                        .get(Constants.FEED_SOURCE, PersistentDataType.STRING);
                FeedSource feedSource = FeedSource.fromString(feedSourceString);
                plugin.getMessageService().sendCommandMessage(sender, "info.feed_source",
                        Map.of("feed_source", feedSource.getDisplayName()));
            }
        }

        return true;
    }
}
