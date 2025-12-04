package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Modern item model provider using ItemMeta.setItemModel() (Minecraft 1.21.4+).
 * This is the recommended approach for custom item models in modern Minecraft versions.
 */
public class ItemModelProvider implements ModelProvider {

    @Override
    public String getName() {
        return "Item Model";
    }

    @Override
    public boolean isAvailable() {
        // Check if the setItemModel method is available (Paper 1.21.4+)
        try {
            ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Override
    public boolean applyModel(ItemStack item, ToolType toolType, String modelId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // Parse the model ID as a namespaced key
        // Expected format: "namespace:key" (e.g., "oaktools:file" or "minecraft:custom/file")
        NamespacedKey key = parseNamespacedKey(modelId);
        if (key == null) {
            return false;
        }

        try {
            meta.setItemModel(key);
            item.setItemMeta(meta);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse a string into a NamespacedKey.
     * Supports formats like "namespace:key" or just "key" (defaults to minecraft namespace).
     *
     * @param modelId the model ID string
     * @return the NamespacedKey, or null if invalid
     */
    private NamespacedKey parseNamespacedKey(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return null;
        }

        // Check if it contains a namespace separator
        int colonIndex = modelId.indexOf(':');
        if (colonIndex > 0) {
            String namespace = modelId.substring(0, colonIndex).toLowerCase();
            String key = modelId.substring(colonIndex + 1).toLowerCase();

            // Validate namespace and key (alphanumeric, underscores, hyphens, dots, slashes)
            if (isValidNamespace(namespace) && isValidKey(key)) {
                return new NamespacedKey(namespace, key);
            }
        } else {
            // No namespace provided, default to minecraft
            String key = modelId.toLowerCase();
            if (isValidKey(key)) {
                return NamespacedKey.minecraft(key);
            }
        }

        return null;
    }

    /**
     * Validate a namespace string (lowercase alphanumeric, underscores, hyphens, dots).
     */
    private boolean isValidNamespace(String namespace) {
        if (namespace.isEmpty()) {
            return false;
        }
        for (char c : namespace.toCharArray()) {
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate a key string (lowercase alphanumeric, underscores, hyphens, dots, slashes).
     */
    private boolean isValidKey(String key) {
        if (key.isEmpty()) {
            return false;
        }
        for (char c : key.toCharArray()) {
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '-' && c != '.' && c != '/') {
                return false;
            }
        }
        return true;
    }
}
