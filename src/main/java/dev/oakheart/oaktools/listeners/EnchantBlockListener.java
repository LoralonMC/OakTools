package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Blocks enchanting table interaction for tools with empty allowed-enchantments.
 * This prevents "locked" tools (harvesting tools) from being placed in the enchanting table.
 */
public class EnchantBlockListener implements Listener {

    private final OakTools plugin;

    public EnchantBlockListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem();
        if (!plugin.getItemFactory().isTool(item)) return;

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType == null) return;

        Set<Enchantment> allowed = plugin.getConfigManager().getAllowedEnchantments(toolType);
        if (allowed.isEmpty()) {
            event.setCancelled(true);
        }
    }
}
