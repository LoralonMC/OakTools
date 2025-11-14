package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

/**
 * Manages model provider detection and application.
 * Determines provider based on model_id prefix (nexo:, itemsadder:, or vanilla).
 */
public class ModelProviderManager {

    private final OakTools plugin;
    private final Logger logger;

    public ModelProviderManager(OakTools plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Initialize and log configured model IDs.
     */
    public void initialize() {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Log model IDs being used
        String fileModel = getModelIdAsString(config, "tools.file.model_id");
        String trowelModel = getModelIdAsString(config, "tools.trowel.model_id");
        String fileProvider = getProviderName(fileModel);
        String trowelProvider = getProviderName(trowelModel);

        logger.info("Model configuration:");
        logger.info("  File: " + fileModel + " (provider: " + fileProvider + ")");
        logger.info("  Trowel: " + trowelModel + " (provider: " + trowelProvider + ")");
    }

    /**
     * Get model ID as string (handles both int and string config values).
     */
    private String getModelIdAsString(FileConfiguration config, String path) {
        if (config.isInt(path)) {
            return String.valueOf(config.getInt(path));
        }
        return config.getString(path, "1001");
    }

    /**
     * Apply model to an item stack.
     * Provider is determined by model_id:
     * - Integer (e.g., 1001) → Vanilla CustomModelData
     * - "nexo:..." → Nexo
     * - "itemsadder:..." → ItemsAdder
     *
     * @param item the item stack
     * @param toolType the tool type
     * @return true if model was applied successfully
     */
    public boolean applyModel(ItemStack item, ToolType toolType) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String toolPath = "tools." + toolType.name().toLowerCase();

        // Check if model_id is an integer (vanilla CustomModelData)
        if (config.isInt(toolPath + ".model_id")) {
            int customModelData = config.getInt(toolPath + ".model_id", 1001);
            VanillaProvider vanillaProvider = new VanillaProvider(customModelData, customModelData);
            return vanillaProvider.applyModel(item, toolType, "");
        }

        // Otherwise, it's a string - check for provider prefix
        String modelId = config.getString(toolPath + ".model_id", "");
        if (modelId.isEmpty()) {
            logger.warning("No model_id configured for " + toolType.name());
            return false;
        }

        // Determine provider based on prefix
        ModelProvider provider;
        String actualModelId = modelId;

        if (modelId.toLowerCase().startsWith("nexo:")) {
            provider = new NexoProvider();
            actualModelId = modelId.substring(5); // Remove "nexo:" prefix

            if (!provider.isAvailable()) {
                logger.warning("Nexo provider requested but Nexo plugin is not available for " + toolType.name());
                return false;
            }
        } else if (modelId.toLowerCase().startsWith("itemsadder:")) {
            provider = new ItemsAdderProvider();
            actualModelId = modelId.substring(11); // Remove "itemsadder:" prefix

            if (!provider.isAvailable()) {
                logger.warning("ItemsAdder provider requested but ItemsAdder plugin is not available for " + toolType.name());
                return false;
            }
        } else {
            // No recognized prefix - treat as vanilla integer
            logger.warning("Unrecognized model_id format '" + modelId + "' for " + toolType.name());
            logger.warning("Use an integer for vanilla CustomModelData, 'nexo:id' for Nexo, or 'itemsadder:id' for ItemsAdder");
            return false;
        }

        boolean success = provider.applyModel(item, toolType, actualModelId);

        if (!success) {
            logger.warning("Failed to apply " + provider.getName() + " model for " + toolType.name());
            logger.warning("Tool will be created without custom model. Check your " + provider.getName() + " configuration.");
        }

        return success;
    }

    /**
     * Get provider name from model ID.
     */
    private String getProviderName(String modelId) {
        // Check if it's a number (vanilla)
        try {
            Integer.parseInt(modelId);
            return "Vanilla";
        } catch (NumberFormatException ignored) {
            // Not a number, check prefix
        }

        if (modelId.toLowerCase().startsWith("nexo:")) {
            return "Nexo";
        } else if (modelId.toLowerCase().startsWith("itemsadder:")) {
            return "ItemsAdder";
        } else {
            return "Vanilla";
        }
    }
}
