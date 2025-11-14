package dev.oakheart.oaktools.integration;

import dev.lone.itemsadder.api.CustomStack;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * ItemsAdder model provider.
 * Applies CustomModelData from ItemsAdder items to our tools.
 */
public class ItemsAdderProvider implements ModelProvider {

    @Override
    public String getName() {
        return "ItemsAdder";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("dev.lone.itemsadder.api.CustomStack");
            return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public boolean applyModel(ItemStack item, ToolType toolType, String modelId) {
        // ItemsAdder uses namespace:id format (e.g., "oaktools:file")
        // We get the CustomModelData from the ItemsAdder item and apply it to our item

        try {
            // Get the ItemsAdder custom item
            CustomStack customStack = CustomStack.getInstance(modelId);

            if (customStack == null) {
                Bukkit.getLogger().warning("[OakTools] ItemsAdder item '" + modelId + "' not found. " +
                        "Make sure you have defined this item in your ItemsAdder config.");
                return false;
            }

            // Get the ItemsAdder item's ItemStack
            ItemStack iaItem = customStack.getItemStack();
            if (iaItem == null || !iaItem.hasItemMeta()) {
                return false;
            }

            // Extract CustomModelData from the ItemsAdder item
            ItemMeta iaMeta = iaItem.getItemMeta();
            if (!iaMeta.hasCustomModelData()) {
                Bukkit.getLogger().warning("[OakTools] ItemsAdder item '" + modelId + "' has no CustomModelData. " +
                        "Make sure it's properly configured in your resource pack.");
                return false;
            }

            int customModelData = iaMeta.getCustomModelData();

            // Apply it to our item
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            meta.setCustomModelData(customModelData);
            item.setItemMeta(meta);

            return true;

        } catch (Exception e) {
            Bukkit.getLogger().warning("[OakTools] Failed to apply ItemsAdder model '" + modelId + "': " + e.getMessage());
            return false;
        }
    }
}
