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

        // Underscores stripped to match the declared nodes: paper-plugin.yml
        // defines oaktools.craft.veinminer (like oaktools.use.veinminer), but
        // VEIN_MINER.name() lowercases to vein_miner — an undefined node that
        // silently defaults to op-only the day the recipe is enabled.
        String permission = "oaktools.craft." + toolType.name().toLowerCase(java.util.Locale.ROOT).replace("_", "");
        if (!event.getWhoClicked().hasPermission(permission)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                plugin.getMessageManager().send(player, "craft-denied");
            }
        }
    }
}
