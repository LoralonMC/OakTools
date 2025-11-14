package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.inventory.ItemStack;

/**
 * Interface for custom model providers (ItemsAdder, Nexo, vanilla).
 */
public interface ModelProvider {

    /**
     * Get the name of this provider.
     *
     * @return the provider name
     */
    String getName();

    /**
     * Check if this provider is available on the server.
     *
     * @return true if the provider's plugin is loaded and available
     */
    boolean isAvailable();

    /**
     * Apply the model to an item stack.
     *
     * @param item the item stack to apply the model to
     * @param toolType the tool type
     * @param modelId the model ID from config
     * @return true if the model was applied successfully
     */
    boolean applyModel(ItemStack item, ToolType toolType, String modelId);
}
