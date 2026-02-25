package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

public class CraftingListener implements Listener {

    private final OakTools plugin;

    public CraftingListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();

        if (!plugin.getItemFactory().isTool(result)) {
            return;
        }

        var toolType = plugin.getItemFactory().getToolType(result);
        if (toolType == null) {
            return;
        }

        String permission = "oaktools.craft." + toolType.name().toLowerCase();
        if (!event.getWhoClicked().hasPermission(permission)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                plugin.getMessageManager().sendMessage(player, "craft-denied");
            }
        }
    }
}
