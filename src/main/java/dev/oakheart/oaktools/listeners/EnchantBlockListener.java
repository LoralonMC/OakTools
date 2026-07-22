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
 * Blocks the enchanting table for all OakTools tools (except sickles, which
 * are vanilla items). The table rolls arbitrary enchants for the base material
 * and cannot be filtered per-enchant, so it would bypass a tool's
 * allowed-enchantments list. Allowed enchants are applied via enchanted books
 * on the anvil instead — AnvilListener filters those results.
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

        // Sickles use vanilla enchanting — don't block
        if (toolType == ToolType.SICKLE) return;

        event.setCancelled(true);
    }
}
