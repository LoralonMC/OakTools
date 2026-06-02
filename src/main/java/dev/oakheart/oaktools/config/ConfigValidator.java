package dev.oakheart.oaktools.config;

import dev.oakheart.config.ConfigManager;
import dev.oakheart.oaktools.model.ToolType;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ConfigValidator {

    public static boolean validate(ConfigManager config, Logger logger) {
        List<String> warnings = new ArrayList<>();
        boolean valid = true;

        for (ToolType toolType : ToolType.values()) {
            valid &= validateTool(config, toolType.getConfigKey(), warnings);
        }
        valid &= validateGeneralSettings(config, warnings);
        valid &= validateDisplaySettings(config, warnings);
        valid &= validateMessageSettings(config, warnings);
        valid &= validateRecipes(config, warnings);
        valid &= validateWandSettings(config, warnings);
        valid &= validateHarvestingSettings(config, warnings);

        if (!warnings.isEmpty()) {
            logger.warning("=== Configuration Warnings ===");
            for (String warning : warnings) {
                logger.warning("  - " + warning);
            }
        }

        return valid;
    }

    // Tools that use a different config structure and skip standard validation
    private static final Set<ToolType> NON_STANDARD_TOOLS = Set.of(ToolType.SICKLE);

    private static boolean validateTool(ConfigManager config, String toolName, List<String> warnings) {
        // Skip tools with non-standard config layouts
        ToolType toolType = ToolType.fromString(toolName);
        if (toolType != null && NON_STANDARD_TOOLS.contains(toolType)) {
            return true;
        }

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
        if (!repairMat.equalsIgnoreCase("NONE") && !repairMat.isEmpty()) {
            try {
                Material.valueOf(repairMat);
            } catch (IllegalArgumentException e) {
                warnings.add(path + ".durability.repair-material '" + repairMat + "' is not a valid material.");
            }
        }

        int repairAmount = config.getInt(path + ".durability.repair-amount", -1);
        if (repairAmount <= 0 && !repairMat.equalsIgnoreCase("NONE")) {
            warnings.add(path + ".durability.repair-amount must be > 0. Found: " + repairAmount);
        }

        return true;
    }

    private static boolean validateGeneralSettings(ConfigManager config, List<String> warnings) {
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

    private static boolean validateDisplaySettings(ConfigManager config, List<String> warnings) {
        for (ToolType toolType : ToolType.values()) {
            if (NON_STANDARD_TOOLS.contains(toolType)) continue;
            String path = "tools." + toolType.getConfigKey() + ".display";
            if (!config.contains(path)) {
                warnings.add("Missing display configuration section: " + path);
                return false;
            }
        }
        return true;
    }

    private static boolean validateMessageSettings(ConfigManager config, List<String> warnings) {
        // Messages are now in messages.yml, no validation needed here
        return true;
    }

    private static boolean validateRecipes(ConfigManager config, List<String> warnings) {
        boolean valid = true;
        for (ToolType toolType : ToolType.values()) {
            if (NON_STANDARD_TOOLS.contains(toolType)) continue;
            String path = "tools." + toolType.getConfigKey() + ".recipe";
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
            var ingredientsSection = config.getSection(path + ".ingredients");
            if (ingredientsSection == null) {
                if (!shapeLetters.isEmpty()) {
                    warnings.add(path + ".ingredients section is missing but shape uses letters: " + shapeLetters);
                    valid = false;
                }
                continue;
            }

            Set<Character> ingredientKeys = new HashSet<>();
            for (String key : ingredientsSection.getKeys(false)) {
                if (key.length() != 1) {
                    warnings.add(path + ".ingredients key '" + key + "' must be a single character.");
                    continue;
                }
                char c = key.charAt(0);
                ingredientKeys.add(c);

                String materialName = ingredientsSection.getString(key, "");
                if (materialName.startsWith("#")) {
                    continue; // Tag-based ingredient (e.g. #planks), validated at registration time
                }
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

    private static boolean validateWandSettings(ConfigManager config, List<String> warnings) {
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

    private static boolean validateHarvestingSettings(ConfigManager config, List<String> warnings) {
        boolean valid = true;
        for (ToolType toolType : List.of(ToolType.EXCAVATOR, ToolType.LUMBERJACK, ToolType.VEIN_MINER)) {
            String key = toolType.getConfigKey();
            String path = "tools." + key;

            if (!config.contains(path)) {
                continue; // Tool section not present, skip (defaults apply)
            }

            int maxBlocks = config.getInt(path + ".max-blocks", 9);
            if (maxBlocks < 1 || maxBlocks > 256) {
                warnings.add(path + ".max-blocks must be 1-256. Found: " + maxBlocks);
                valid = false;
            }

            int breakSpeed = config.getInt(path + ".break-speed-ticks", 1);
            if (breakSpeed < 1 || breakSpeed > 20) {
                warnings.add(path + ".break-speed-ticks must be 1-20. Found: " + breakSpeed);
            }
        }

        int minLeaves = config.getInt("tools.lumberjack.min-leaves", 5);
        if (minLeaves < 1 || minLeaves > 20) {
            warnings.add("tools.lumberjack.min-leaves must be 1-20. Found: " + minLeaves);
        }

        int gridSize = config.getInt("tools.excavator.grid-size", 3);
        if (gridSize % 2 == 0) {
            warnings.add("tools.excavator.grid-size should be an odd number; " + gridSize
                    + " will be rounded up to " + (gridSize + 1) + ".");
        }

        return valid;
    }
}
