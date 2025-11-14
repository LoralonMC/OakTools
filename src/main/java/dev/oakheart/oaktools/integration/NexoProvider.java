package dev.oakheart.oaktools.integration;

import com.nexomc.nexo.api.NexoItems;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Nexo model provider.
 * Applies CustomModelData from Nexo items to our tools.
 */
public class NexoProvider implements ModelProvider {

    @Override
    public String getName() {
        return "Nexo";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("com.nexomc.nexo.api.NexoItems");
            return Bukkit.getPluginManager().getPlugin("Nexo") != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public boolean applyModel(ItemStack item, ToolType toolType, String modelId) {
        // Nexo can use either namespace:id format (e.g., "oaktools:file") or simple ID (e.g., "file")
        // We get the CustomModelData from the Nexo item and apply it to our item

        try {
            // Get the Nexo item by ID (returns ItemBuilder)
            var nexoItemBuilder = NexoItems.itemFromId(modelId);

            if (nexoItemBuilder == null) {
                Bukkit.getLogger().warning("[OakTools] Nexo item '" + modelId + "' not found. " +
                        "Make sure you have defined this item in your Nexo config.");
                return false;
            }

            // Build the ItemStack from the builder
            ItemStack nexoItem = nexoItemBuilder.build();

            if (nexoItem == null || !nexoItem.hasItemMeta()) {
                Bukkit.getLogger().warning("[OakTools] Nexo item '" + modelId + "' has no metadata.");
                return false;
            }

            // Extract CustomModelData from the Nexo item
            ItemMeta nexoMeta = nexoItem.getItemMeta();
            if (!nexoMeta.hasCustomModelData()) {
                Bukkit.getLogger().warning("[OakTools] Nexo item '" + modelId + "' has no CustomModelData. " +
                        "Make sure it's properly configured in your resource pack.");
                return false;
            }

            int customModelData = nexoMeta.getCustomModelData();

            // Apply it to our item
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            meta.setCustomModelData(customModelData);
            item.setItemMeta(meta);

            return true;

        } catch (Exception e) {
            Bukkit.getLogger().warning("[OakTools] Failed to apply Nexo model '" + modelId + "': " + e.getMessage());
            return false;
        }
    }
}
