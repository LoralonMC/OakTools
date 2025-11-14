package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.Constants;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles anvil repair and combining for OakTools tools.
 */
public class AnvilListener implements Listener {

    private final OakTools plugin;

    public AnvilListener(OakTools plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if debug logging is enabled in config.
     */
    private boolean isDebugEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("general.debug", false);
    }

    /**
     * Block anvil results with disallowed enchantments and fix display formatting.
     * Runs at HIGHEST to be the last non-monitor handler.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilResultFilter(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null) {
            return;
        }

        if (!plugin.getItemFactory().isTool(result)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(result);
        if (toolType == null) {
            return;
        }

        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return;
        }

        // Check for any disallowed enchantments
        Map<Enchantment, Integer> enchants = meta.getEnchants();
        if (!enchants.isEmpty()) {
            // Get allowed enchantments
            String toolPath = "tools." + toolType.name().toLowerCase();
            List<String> allowedEnchantNames = plugin.getConfigManager().getConfig()
                    .getStringList(toolPath + ".allowed_enchantments");

            Set<Enchantment> allowedEnchants = allowedEnchantNames.stream()
                    .map(Enchantment::getByName)
                    .filter(enchant -> enchant != null)
                    .collect(Collectors.toSet());

            for (Enchantment enchant : enchants.keySet()) {
                if (!allowedEnchants.contains(enchant)) {
                    // Block the result entirely - don't allow taking it
                    event.setResult(null);
                    if (isDebugEnabled()) {
                        plugin.getLogger().info("[Anvil Debug] Blocked result with disallowed enchantment: " + enchant.getKey());
                    }
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack first = inventory.getItem(0);
        ItemStack second = inventory.getItem(1);

        if (first == null || second == null) {
            return;
        }

        // Check if first item is an OakTools tool
        if (!plugin.getItemFactory().isTool(first)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(first);
        if (toolType == null) {
            return;
        }

        FileConfiguration config = plugin.getConfigManager().getConfig();
        String repairMaterialName = config.getString("tools." + toolType.name().toLowerCase() + ".durability.repair_material", "IRON_INGOT");
        Material repairMaterial;

        try {
            repairMaterial = Material.valueOf(repairMaterialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid repair material for " + toolType + ": " + repairMaterialName);
            return;
        }

        // Case 1: Repair with material
        if (second.getType() == repairMaterial) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[Anvil Debug] Repairing " + toolType + " with " + repairMaterial);
            }
            handleMaterialRepair(event, first, second, toolType);
        }
        // Case 2: Combine two tools
        else if (plugin.getItemFactory().isTool(second) &&
                 plugin.getItemFactory().getToolType(second) == toolType) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[Anvil Debug] Combining two " + toolType + " tools");
            }
            handleToolCombine(event, first, second, toolType);
        }
        // Case 3: Enchanted books
        // Let vanilla handle book application, onAnvilResultFilter will block disallowed enchantments
    }

    /**
     * Handle repairing a tool with repair material.
     */
    @SuppressWarnings("removal")
    private void handleMaterialRepair(PrepareAnvilEvent event, ItemStack tool, ItemStack material, ToolType toolType) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int repairPerItem = config.getInt("tools." + toolType.name().toLowerCase() + ".durability.repair_amount", 63);

        int currentDamage = plugin.getDurabilityService().getCurrentDamage(tool);
        int maxDurability = plugin.getDurabilityService().getMaxDurability(tool);

        if (currentDamage <= 0) {
            // Already at full durability
            if (isDebugEnabled()) {
                plugin.getLogger().info("[Anvil Debug] Tool already at full durability, no repair needed");
            }
            return;
        }

        // Calculate repair amount (based on number of items used)
        int itemsUsed = Math.min(material.getAmount(), (int) Math.ceil((double) currentDamage / repairPerItem));
        int totalRepair = itemsUsed * repairPerItem;

        if (isDebugEnabled()) {
            plugin.getLogger().info("[Anvil Debug] Current damage: " + currentDamage + "/" + maxDurability);
            plugin.getLogger().info("[Anvil Debug] Using " + itemsUsed + " x " + material.getType() +
                " to repair " + totalRepair + " durability");
        }

        // Create result item
        ItemStack result = tool.clone();
        plugin.getDurabilityService().repair(result, totalRepair);

        // Update display (only updates lore, never name - players can rename freely)
        plugin.getDisplayService().updateDisplay(result);

        event.setResult(result);

        // Must set repair cost for material repairs (vanilla can't calculate cost for custom repair materials)
        // Without this, the anvil shows the result but won't let players take it
        int repairCost = itemsUsed;
        event.getInventory().setRepairCost(repairCost);

        if (isDebugEnabled()) {
            int finalDamage = plugin.getDurabilityService().getCurrentDamage(result);
            plugin.getLogger().info("[Anvil Debug] Final damage: " + finalDamage + "/" + maxDurability +
                " (repair cost: " + repairCost + " levels)");
        }
    }

    /**
     * Handle combining two tools.
     */
    private void handleToolCombine(PrepareAnvilEvent event, ItemStack first, ItemStack second, ToolType toolType) {
        int firstDamage = plugin.getDurabilityService().getCurrentDamage(first);
        int secondDamage = plugin.getDurabilityService().getCurrentDamage(second);
        int firstMax = plugin.getDurabilityService().getMaxDurability(first);
        int secondMax = plugin.getDurabilityService().getMaxDurability(second);

        if (isDebugEnabled()) {
            plugin.getLogger().info("[Anvil Debug] First tool: " + firstDamage + "/" + firstMax +
                " (remaining: " + (firstMax - firstDamage) + ")");
            plugin.getLogger().info("[Anvil Debug] Second tool: " + secondDamage + "/" + secondMax +
                " (remaining: " + (secondMax - secondDamage) + ")");
        }

        // Calculate combined durability (vanilla logic: add remaining + 5% bonus)
        int firstRemaining = firstMax - firstDamage;
        int secondRemaining = secondMax - secondDamage;
        int combinedRemaining = firstRemaining + secondRemaining + (int) (firstMax * 0.05);

        // Choose the higher max durability
        int resultMax = Math.max(firstMax, secondMax);

        // Calculate new damage
        int resultDamage = Math.max(0, resultMax - combinedRemaining);

        if (isDebugEnabled()) {
            plugin.getLogger().info("[Anvil Debug] Combined remaining: " + combinedRemaining +
                " (with 5% bonus: " + (int) (firstMax * 0.05) + ")");
            plugin.getLogger().info("[Anvil Debug] Result: " + resultDamage + "/" + resultMax);
        }

        // Create result item (clone first item to preserve enchantments and metadata)
        ItemStack result = first.clone();
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) {
            return;
        }

        // Copy all PDC data from first item
        PersistentDataContainer firstPDC = first.getItemMeta().getPersistentDataContainer();
        PersistentDataContainer resultPDC = resultMeta.getPersistentDataContainer();

        // Copy all keys with proper type handling
        for (var key : firstPDC.getKeys()) {
            // Handle known types from Constants
            if (key.equals(Constants.TOOL_TYPE) || key.equals(Constants.FEED_SOURCE)) {
                // String types
                String value = firstPDC.get(key, PersistentDataType.STRING);
                if (value != null) {
                    resultPDC.set(key, PersistentDataType.STRING, value);
                }
            } else if (key.equals(Constants.DURABILITY) || key.equals(Constants.MAX_DURABILITY)) {
                // Integer types (these will be overwritten below, but copy for consistency)
                Integer value = firstPDC.get(key, PersistentDataType.INTEGER);
                if (value != null) {
                    resultPDC.set(key, PersistentDataType.INTEGER, value);
                }
            } else {
                // Unknown key - try to copy as string (best effort)
                String value = firstPDC.get(key, PersistentDataType.STRING);
                if (value != null) {
                    resultPDC.set(key, PersistentDataType.STRING, value);
                }
            }
        }

        // Update durability
        resultPDC.set(Constants.DURABILITY, PersistentDataType.INTEGER, resultDamage);
        resultPDC.set(Constants.MAX_DURABILITY, PersistentDataType.INTEGER, resultMax);

        result.setItemMeta(resultMeta);

        // Sync vanilla durability and update display (only updates lore, never name)
        plugin.getItemFactory().syncVanillaDurability(result);
        plugin.getDisplayService().updateDisplay(result);

        event.setResult(result);

        // XP cost will be calculated by vanilla/Paper automatically
    }

    /**
     * Handle taking the result from the anvil to refund excess materials.
     * Let vanilla consume all items, then refund the excess on the next tick.
     * Only runs if the click is actually successful (not cancelled).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilClick(InventoryClickEvent event) {
        // Only handle anvil inventories
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }

        // Only handle clicking the result slot (slot 2)
        if (event.getRawSlot() != 2) {
            return;
        }

        ItemStack result = anvil.getItem(2);
        ItemStack first = anvil.getItem(0);
        ItemStack second = anvil.getItem(1);

        // Check if we have a result and it's an OakTools tool
        if (result == null || first == null || second == null) {
            return;
        }

        if (!plugin.getItemFactory().isTool(first)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(first);
        if (toolType == null) {
            return;
        }

        // Check if this is a material repair (not combining two tools)
        if (plugin.getItemFactory().isTool(second)) {
            return; // This is combining tools, let vanilla handle it
        }

        // Get repair material config
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String repairMaterialName = config.getString("tools." + toolType.name().toLowerCase() + ".durability.repair_material", "IRON_INGOT");
        Material repairMaterial;

        try {
            repairMaterial = Material.valueOf(repairMaterialName);
        } catch (IllegalArgumentException e) {
            return;
        }

        // Check if second item is the repair material
        if (second.getType() != repairMaterial) {
            return;
        }

        // Calculate how many items should actually be consumed
        int repairPerItem = config.getInt("tools." + toolType.name().toLowerCase() + ".durability.repair_amount", 63);
        int currentDamage = plugin.getDurabilityService().getCurrentDamage(first);
        int itemsNeeded = Math.min(second.getAmount(), (int) Math.ceil((double) currentDamage / repairPerItem));

        if (itemsNeeded <= 0) {
            return;
        }

        // Calculate how many items to refund
        int itemsToRefund = second.getAmount() - itemsNeeded;

        if (itemsToRefund > 0) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[Anvil Debug] Refunding " + itemsToRefund + " x " + repairMaterial +
                    " (only needed " + itemsNeeded + " out of " + second.getAmount() + ")");
            }

            // Get player
            if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
                return;
            }

            // Schedule a task to run next tick to refund extra materials
            // (after vanilla anvil has consumed the items)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Give back the extra materials
                ItemStack refund = new ItemStack(repairMaterial, itemsToRefund);
                player.getInventory().addItem(refund).forEach((index, leftover) -> {
                    // If inventory is full, drop at player's location
                    player.getWorld().dropItem(player.getLocation(), leftover);
                });

                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Anvil Debug] Refunded " + itemsToRefund + " x " + repairMaterial + " to player");
                }
            });
        }
    }
}
