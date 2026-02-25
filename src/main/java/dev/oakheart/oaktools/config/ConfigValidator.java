package dev.oakheart.oaktools.config;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ConfigValidator {

    public static boolean validate(FileConfiguration config, Logger logger) {
        List<String> warnings = new ArrayList<>();
        boolean valid = true;

        valid &= validateTool(config, "file", warnings);
        valid &= validateTool(config, "trowel", warnings);
        valid &= validateTool(config, "wand", warnings);
        valid &= validateGeneralSettings(config, warnings);
        valid &= validateDisplaySettings(config, warnings);
        valid &= validateMessageSettings(config, warnings);
        valid &= validateRecipes(config, warnings);
        valid &= validateWandSettings(config, warnings);

        if (!warnings.isEmpty()) {
            logger.warning("=== Configuration Warnings ===");
            for (String warning : warnings) {
                logger.warning("  - " + warning);
            }
        }

        return valid;
    }

    private static boolean validateTool(FileConfiguration config, String toolName, List<String> warnings) {
        String path = "tools." + toolName;

        if (!config.contains(path)) {
            warnings.add("Missing configuration section: " + path);
            return false;
        }

        int maxDur = config.getInt(path + ".durability.max", -1);
        if (maxDur <= 0) {
            warnings.add(path + ".durability.max must be > 0. Found: " + maxDur);
            return false;
        }

        String repairMat = config.getString(path + ".durability.repair-material", "");
        try {
            Material.valueOf(repairMat);
        } catch (IllegalArgumentException e) {
            warnings.add(path + ".durability.repair-material '" + repairMat + "' is not a valid material.");
        }

        int repairAmount = config.getInt(path + ".durability.repair-amount", -1);
        if (repairAmount <= 0) {
            warnings.add(path + ".durability.repair-amount must be > 0. Found: " + repairAmount);
        }

        return true;
    }

    private static boolean validateGeneralSettings(FileConfiguration config, List<String> warnings) {
        int threshold = config.getInt("general.durability-warning-threshold", 20);
        if (threshold < 0 || threshold > 100) {
            warnings.add("general.durability-warning-threshold must be 0-100. Found: " + threshold);
        }

        String worldsMode = config.getString("general.restrictions.worlds.mode", "WHITELIST");
        if (!worldsMode.equals("WHITELIST") && !worldsMode.equals("BLACKLIST")) {
            warnings.add("general.restrictions.worlds.mode must be 'WHITELIST' or 'BLACKLIST'. Found: " + worldsMode);
            return false;
        }
        return true;
    }

    private static boolean validateDisplaySettings(FileConfiguration config, List<String> warnings) {
        for (String toolName : List.of("file", "trowel", "wand")) {
            String path = "tools." + toolName + ".display";
            if (!config.contains(path)) {
                warnings.add("Missing display configuration section: " + path);
                return false;
            }
        }
        return true;
    }

    private static boolean validateMessageSettings(FileConfiguration config, List<String> warnings) {
        if (!config.contains("messages")) {
            warnings.add("Missing messages configuration section.");
            return false;
        }

        var section = config.getConfigurationSection("messages");
        if (section == null) {
            warnings.add("Missing messages configuration section.");
            return false;
        }

        for (String key : section.getKeys(false)) {
            if (key.equals("commands")) {
                continue;
            }
            var msgSection = section.getConfigurationSection(key);
            if (msgSection != null && msgSection.contains("display")) {
                String display = msgSection.getString("display", "");
                if (!display.equals("chat") && !display.equals("action_bar") && !display.equals("title")) {
                    warnings.add("messages." + key + ".display contains invalid method: " + display);
                }
            }
        }

        return true;
    }

    private static boolean validateRecipes(FileConfiguration config, List<String> warnings) {
        boolean valid = true;
        for (String toolName : List.of("file", "trowel", "wand")) {
            String path = "tools." + toolName + ".recipe";
            if (!config.getBoolean(path + ".enabled", false)) {
                continue;
            }

            // Validate shape
            List<String> shape = config.getStringList(path + ".shape");
            if (shape.size() != 3) {
                warnings.add(path + ".shape must have exactly 3 rows. Found: " + shape.size());
                valid = false;
                continue;
            }

            Set<Character> shapeLetters = new HashSet<>();
            boolean shapeValid = true;
            for (int i = 0; i < shape.size(); i++) {
                String row = shape.get(i);
                if (row.length() > 3) {
                    warnings.add(path + ".shape row " + (i + 1) + " exceeds 3 characters: \"" + row + "\"");
                    shapeValid = false;
                }
                for (char c : row.toCharArray()) {
                    if (c != ' ') {
                        shapeLetters.add(c);
                    }
                }
            }

            if (!shapeValid) {
                valid = false;
                continue;
            }

            // Validate ingredients
            ConfigurationSection ingredients = config.getConfigurationSection(path + ".ingredients");
            if (ingredients == null) {
                if (!shapeLetters.isEmpty()) {
                    warnings.add(path + ".ingredients section is missing but shape uses letters: " + shapeLetters);
                    valid = false;
                }
                continue;
            }

            Set<Character> ingredientKeys = new HashSet<>();
            for (String key : ingredients.getKeys(false)) {
                if (key.length() != 1) {
                    warnings.add(path + ".ingredients key '" + key + "' must be a single character.");
                    continue;
                }
                char c = key.charAt(0);
                ingredientKeys.add(c);

                String materialName = ingredients.getString(key, "");
                try {
                    Material.valueOf(materialName);
                } catch (IllegalArgumentException e) {
                    warnings.add(path + ".ingredients." + key + " has invalid material: " + materialName);
                    valid = false;
                }
            }

            // Cross-reference shape letters with ingredient keys
            for (char letter : shapeLetters) {
                if (!ingredientKeys.contains(letter)) {
                    warnings.add(path + ": shape uses letter '" + letter + "' but no ingredient is defined for it.");
                    valid = false;
                }
            }
            for (char key : ingredientKeys) {
                if (!shapeLetters.contains(key)) {
                    warnings.add(path + ": ingredient '" + key + "' is defined but not used in shape.");
                }
            }
        }
        return valid;
    }

    private static boolean validateWandSettings(FileConfiguration config, List<String> warnings) {
        int maxBlocks = config.getInt("tools.wand.max-blocks", 64);
        if (maxBlocks < 1 || maxBlocks > 1024) {
            warnings.add("tools.wand.max-blocks must be 1-1024. Found: " + maxBlocks);
            return false;
        }

        int undoMaxHistory = config.getInt("tools.wand.undo.max-history", 5);
        if (undoMaxHistory < 1 || undoMaxHistory > 50) {
            warnings.add("tools.wand.undo.max-history must be 1-50. Found: " + undoMaxHistory);
        }

        int undoExpireSeconds = config.getInt("tools.wand.undo.expire-seconds", 300);
        if (undoExpireSeconds < 10 || undoExpireSeconds > 3600) {
            warnings.add("tools.wand.undo.expire-seconds must be 10-3600. Found: " + undoExpireSeconds);
        }

        int previewInterval = config.getInt("tools.wand.preview.interval-ticks", 5);
        if (previewInterval < 1 || previewInterval > 40) {
            warnings.add("tools.wand.preview.interval-ticks must be 1-40. Found: " + previewInterval);
        }

        String glowColor = config.getString("tools.wand.preview.glow-color", "YELLOW");
        if (NamedTextColor.NAMES.value(glowColor.toLowerCase()) == null) {
            warnings.add("tools.wand.preview.glow-color '" + glowColor + "' is not a valid color. Valid: " +
                    String.join(", ", NamedTextColor.NAMES.keys()));
        }

        return true;
    }
}
