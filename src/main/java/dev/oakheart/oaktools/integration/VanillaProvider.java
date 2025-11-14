package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Vanilla CustomModelData provider (always available as fallback).
 */
public class VanillaProvider implements ModelProvider {

    private final int fileModelData;
    private final int trowelModelData;

    public VanillaProvider(int fileModelData, int trowelModelData) {
        this.fileModelData = fileModelData;
        this.trowelModelData = trowelModelData;
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

        int customModelData = switch (toolType) {
            case FILE -> fileModelData;
            case TROWEL -> trowelModelData;
        };

        meta.setCustomModelData(customModelData);
        item.setItemMeta(meta);
        return true;
    }
}
