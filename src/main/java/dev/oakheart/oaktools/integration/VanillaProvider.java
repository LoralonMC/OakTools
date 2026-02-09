package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Vanilla CustomModelData provider (always available as fallback).
 */
public class VanillaProvider implements ModelProvider {

    private final int customModelData;

    public VanillaProvider(int customModelData) {
        this.customModelData = customModelData;
    }

    @Override
    public String getName() {
        return "Vanilla CustomModelData";
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available
    }

    @Override
    public boolean applyModel(ItemStack item, ToolType toolType, String modelId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        meta.setCustomModelData(customModelData);
        item.setItemMeta(meta);
        return true;
    }
}
