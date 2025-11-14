package dev.oakheart.oaktools.commands.subcommands;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.command.CommandSender;

/**
 * /oaktools reload
 */
public class ReloadCommand {

    private final OakTools plugin;

    public ReloadCommand(OakTools plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("oaktools.reload")) {
            plugin.getMessageService().sendCommandMessage(sender, "no_permission");
            return true;
        }

        plugin.getMessageService().sendCommandMessage(sender, "reload.reloading");

        // Reload config
        boolean success = plugin.getConfigManager().reload();

        if (!success) {
            plugin.getMessageService().sendCommandMessage(sender, "reload.failed");
            return true;
        }

        // Reload recipes (unregister old, register new)
        plugin.getRecipeManager().unregisterRecipes();
        plugin.getRecipeManager().registerRecipes();

        plugin.getMessageService().sendCommandMessage(sender, "reload.success");

        return true;
    }
}
