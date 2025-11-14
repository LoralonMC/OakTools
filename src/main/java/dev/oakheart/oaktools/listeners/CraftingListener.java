package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles crafting permission checks for OakTools recipes.
 */
public class CraftingListener implements Listener {

    private final OakTools plugin;

    public CraftingListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();

        // Check if the result is an OakTools tool
        if (!plugin.getItemFactory().isTool(result)) {
            return;
        }

        var toolType = plugin.getItemFactory().getToolType(result);
        if (toolType == null) {
            return;
        }

        // Check permission
        String permission = "oaktools.craft." + toolType.name().toLowerCase();
        if (!event.getWhoClicked().hasPermission(permission)) {
            event.setCancelled(true);
            plugin.getMessageService().sendDirectActionBar(
                    (org.bukkit.entity.Player) event.getWhoClicked(),
                    "<red>You don't have permission to craft this tool</red>"
            );
        }
    }
}
