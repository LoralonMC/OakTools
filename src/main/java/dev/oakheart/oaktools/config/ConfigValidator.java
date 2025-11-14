package dev.oakheart.oaktools.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.logging.Logger;

/**
 * Validates configuration values on load/reload.
 */
public class ConfigValidator {

    /**
     * Validate the configuration and log warnings for invalid values.
     *
     * @param config the configuration to validate
     * @param logger the logger to use for warnings
     * @return true if config is valid (or has fixable issues), false if critically invalid
     */
    public static boolean validate(FileConfiguration config, Logger logger) {
        boolean valid = true;

        // Validate tool configurations
        valid &= validateTool(config, "file", logger);
        valid &= validateTool(config, "trowel", logger);

        // Validate general settings
        valid &= validateGeneralSettings(config, logger);

        // Validate display settings
        valid &= validateDisplaySettings(config, logger);

        // Validate message settings
        valid &= validateMessageSettings(config, logger);

        return valid;
    }

    private static boolean validateTool(FileConfiguration config, String toolName, Logger logger) {
        String path = "tools." + toolName;
        ConfigurationSection section = config.getConfigurationSection(path);

        if (section == null) {
            logger.warning("Missing configuration section: " + path);
            return false;
        }

        // Validate durability.max
        int maxDur = section.getInt("durability.max", -1);
        if (maxDur <= 0) {
            logger.warning(path + ".durability.max must be > 0. Found: " + maxDur);
        }

        // Validate durability.repair_material
        String repairMat = section.getString("durability.repair_material", "");
        try {
            Material.valueOf(repairMat);
        } catch (IllegalArgumentException e) {
            logger.warning(path + ".durability.repair_material '" + repairMat + "' is not a valid material.");
        }

        // Validate durability.repair_amount
        int repairAmount = section.getInt("durability.repair_amount", -1);
        if (repairAmount <= 0) {
            logger.warning(path + ".durability.repair_amount must be > 0. Found: " + repairAmount);
        }

        return true;
    }

    private static boolean validateGeneralSettings(FileConfiguration config, Logger logger) {
        String worldsMode = config.getString("general.restrictions.worlds.mode", "WHITELIST");
        if (!worldsMode.equals("WHITELIST") && !worldsMode.equals("BLACKLIST")) {
            logger.warning("general.restrictions.worlds.mode must be 'WHITELIST' or 'BLACKLIST'. Found: " + worldsMode);
        }

        return true;
    }

    private static boolean validateDisplaySettings(FileConfiguration config, Logger logger) {
        // Validate per-tool display settings exist
        for (String toolName : List.of("file", "trowel")) {
            String path = "tools." + toolName + ".display";
            ConfigurationSection display = config.getConfigurationSection(path);
            if (display == null) {
                logger.warning("Missing display configuration section: " + path);
            }
        }

        return true;
    }

    private static boolean validateMessageSettings(FileConfiguration config, Logger logger) {
        // Validate message delivery methods
        ConfigurationSection messages = config.getConfigurationSection("messages");
        if (messages == null) {
            logger.warning("Missing messages configuration section.");
            return false;
        }

        for (String key : messages.getKeys(false)) {
            ConfigurationSection msg = messages.getConfigurationSection(key);
            if (msg != null) {
                List<String> delivery = msg.getStringList("delivery");
                for (String method : delivery) {
                    if (!method.equalsIgnoreCase("actionbar") &&
                        !method.equalsIgnoreCase("chat") &&
                        !method.equalsIgnoreCase("title")) {
                        logger.warning("messages." + key + ".delivery contains invalid method: " + method);
                    }
                }
            }
        }

        return true;
    }
}
