package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

public class ModelProviderManager {

    private final OakTools plugin;
    private final Logger logger;

    public ModelProviderManager(OakTools plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void initialize() {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        String fileModel = getModelIdAsString(config, "tools.file.model-id");
        String trowelModel = getModelIdAsString(config, "tools.trowel.model-id");
        String wandModel = getModelIdAsString(config, "tools.wand.model-id");
        String fileProvider = getProviderName(fileModel);
        String trowelProvider = getProviderName(trowelModel);
        String wandProvider = getProviderName(wandModel);

        logger.info("Model configuration:");
        logger.info("  File: " + fileModel + " (provider: " + fileProvider + ")");
        logger.info("  Trowel: " + trowelModel + " (provider: " + trowelProvider + ")");
        logger.info("  Wand: " + wandModel + " (provider: " + wandProvider + ")");
    }

    /**
     * Get model ID as string. Handles both int and string config values.
     * FileConfiguration stores unquoted integers as Integer objects.
     */
    private String getModelIdAsString(FileConfiguration config, String path) {
        Object raw = config.get(path);
        if (raw instanceof Number number) {
            return String.valueOf(number.intValue());
        }
        return config.getString(path, "1001");
    }

    public boolean applyModel(ItemStack item, ToolType toolType) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String path = "tools." + toolType.getConfigKey() + ".model-id";
        return applyModelFromPath(item, toolType, config, path);
    }

    /**
     * Applies a model using a model ID string directly (for tools with non-standard config paths like sickle tiers).
     */
    public boolean applyModelById(ItemStack item, ToolType toolType, String modelId) {
        if (modelId == null || modelId.isEmpty()) return false;
        return applyModelString(item, toolType, modelId);
    }

    private boolean applyModelFromPath(ItemStack item, ToolType toolType, FileConfiguration config, String path) {
        // Check if model-id is an integer (vanilla CustomModelData)
        Object raw = config.get(path);
        if (raw instanceof Number number) {
            int customModelData = number.intValue();
            VanillaProvider vanillaProvider = new VanillaProvider(customModelData);
            return vanillaProvider.applyModel(item, toolType, "");
        }

        String modelId = config.getString(path, "");
        if (modelId.isEmpty()) {
            logger.warning("No model-id configured for " + toolType.name());
            return false;
        }

        return applyModelString(item, toolType, modelId);
    }

    private boolean applyModelString(ItemStack item, ToolType toolType, String modelId) {
        // Check if it's a plain integer written as a string
        try {
            int customModelData = Integer.parseInt(modelId);
            VanillaProvider vanillaProvider = new VanillaProvider(customModelData);
            return vanillaProvider.applyModel(item, toolType, "");
        } catch (NumberFormatException ignored) {
            // Not an integer, check for provider prefix
        }

        ModelProvider provider;
        String actualModelId = modelId;

        if (modelId.toLowerCase().startsWith("model:")) {
            provider = new ItemModelProvider();
            actualModelId = modelId.substring(6);

            if (!provider.isAvailable()) {
                logger.warning("Item Model provider requested but not available (requires Paper 1.21.4+) for " + toolType.name());
                logger.warning("Falling back to no custom model. Consider using CustomModelData instead.");
                return false;
            }
        } else if (modelId.toLowerCase().startsWith("nexo:")) {
            actualModelId = modelId.substring(5);

            provider = new NexoProvider(logger);
            if (!provider.isAvailable()) {
                logger.warning("Nexo provider requested but Nexo plugin is not available for " + toolType.name());
                return false;
            }
        } else if (modelId.toLowerCase().startsWith("itemsadder:")) {
            actualModelId = modelId.substring(11);

            provider = new ItemsAdderProvider(logger);
            if (!provider.isAvailable()) {
                logger.warning("ItemsAdder provider requested but ItemsAdder plugin is not available for " + toolType.name());
                return false;
            }
        } else {
            logger.warning("Unrecognized model-id format '" + modelId + "' for " + toolType.name());
            logger.warning("Use an integer for vanilla CustomModelData, 'model:namespace:key' for Item Model, 'nexo:id' for Nexo, or 'itemsadder:id' for ItemsAdder");
            return false;
        }

        boolean success = provider.applyModel(item, toolType, actualModelId);

        if (!success) {
            logger.warning("Failed to apply " + provider.getName() + " model for " + toolType.name());
            logger.warning("Tool will be created without custom model. Check your " + provider.getName() + " configuration.");
        }

        return success;
    }

    private String getProviderName(String modelId) {
        try {
            Integer.parseInt(modelId);
            return "Vanilla CustomModelData";
        } catch (NumberFormatException ignored) {
        }

        if (modelId.toLowerCase().startsWith("model:")) {
            return "Item Model";
        } else if (modelId.toLowerCase().startsWith("nexo:")) {
            return "Nexo";
        } else if (modelId.toLowerCase().startsWith("itemsadder:")) {
            return "ItemsAdder";
        } else {
            return "Unknown";
        }
    }
}
