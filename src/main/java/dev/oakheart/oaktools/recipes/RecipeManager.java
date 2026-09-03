package dev.oakheart.oaktools.recipes;

import dev.oakheart.config.ConfigManager;
import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.List;
import java.util.Map;

public class RecipeManager {

    // Tag-based ingredient mapping for config values starting with "#"
    private static final Map<String, Tag<Material>> TAG_MAP = Map.of(
            "#planks", Tag.PLANKS,
            "#logs", Tag.LOGS
    );

    private final OakTools plugin;

    public RecipeManager(OakTools plugin) {
        this.plugin = plugin;
    }

    public void registerRecipes() {
        for (ToolType toolType : ToolType.values()) {
            if (toolType == ToolType.SICKLE) {
                registerSickleRecipes();
                continue;
            }
            registerRecipe(toolType);
        }
    }

    private void registerRecipe(ToolType toolType) {
        ConfigManager config = plugin.getConfigManager().getConfig();
        String toolName = toolType.getConfigKey();

        if (!config.getBoolean("tools." + toolName + ".recipe.enabled", true)) {
            return;
        }

        String recipePath = "tools." + toolName + ".recipe";
        if (!config.contains(recipePath)) {
            plugin.getLogger().warning("Missing recipe configuration for " + toolName);
            return;
        }

        List<String> shapeList = config.getStringList(recipePath + ".shape");
        if (shapeList.size() != 3) {
            plugin.getLogger().warning("Invalid recipe shape for " + toolName + " (must be 3 rows)");
            return;
        }

        String[] shape = shapeList.toArray(new String[0]);

        ConfigManager ingredientsSection = config.getSection(recipePath + ".ingredients");
        if (ingredientsSection == null) {
            plugin.getLogger().warning("Missing ingredients for " + toolName);
            return;
        }

        ItemStack result = plugin.getItemFactory().createTool(toolType, 0);

        NamespacedKey key = new NamespacedKey(plugin, toolName + "_recipe");
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape);

        applyCategory(recipe, config.getString(recipePath + ".category", "EQUIPMENT"), toolName);
        applyIngredients(recipe, ingredientsSection, toolName);

        try {
            plugin.getServer().addRecipe(recipe);
            plugin.getLogger().info("Registered recipe for " + toolType.getDisplayName());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register recipe for " + toolName + ": " + e.getMessage());
        }
    }

    private void registerSickleRecipes() {
        ConfigManager config = plugin.getConfigManager().getConfig();

        for (String tier : plugin.getConfigManager().getSickleTiers()) {
            // Netherite is a smithing-table upgrade, handled by SmithingListener.
            // It can't be a SmithingTransformRecipe: the base ingredient would
            // have to be a MaterialChoice of DIAMOND_HOE, which also matches a
            // plain vanilla diamond hoe and would turn it into a sickle.
            if (tier.equalsIgnoreCase("netherite")) {
                continue;
            }

            String recipePath = "tools.sickle.tiers." + tier + ".recipe";
            if (!config.getBoolean(recipePath + ".enabled", false)) continue;

            List<String> shapeList = config.getStringList(recipePath + ".shape");
            if (shapeList.size() != 3) {
                plugin.getLogger().warning("Invalid sickle recipe shape for tier " + tier);
                continue;
            }

            ConfigManager ingredientsSection = config.getSection(recipePath + ".ingredients");
            if (ingredientsSection == null) {
                plugin.getLogger().warning("Missing sickle recipe ingredients for tier " + tier);
                continue;
            }

            ItemStack result = plugin.getItemFactory().createSickle(tier);
            NamespacedKey key = new NamespacedKey(plugin, "sickle_" + tier + "_recipe");
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(shapeList.toArray(new String[0]));

            applyCategory(recipe, config.getString(recipePath + ".category", "EQUIPMENT"), "sickle_" + tier);
            applyIngredients(recipe, ingredientsSection, "sickle_" + tier);

            try {
                plugin.getServer().addRecipe(recipe);
                plugin.getLogger().info("Registered recipe for " + tier + " sickle");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to register sickle recipe for " + tier + ": " + e.getMessage());
            }
        }
    }

    private void applyCategory(ShapedRecipe recipe, String categoryString, String toolName) {
        try {
            org.bukkit.inventory.recipe.CraftingBookCategory category =
                    org.bukkit.inventory.recipe.CraftingBookCategory.valueOf(categoryString);
            recipe.setCategory(category);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid recipe category for " + toolName + ": " + categoryString + ", using EQUIPMENT");
            recipe.setCategory(org.bukkit.inventory.recipe.CraftingBookCategory.EQUIPMENT);
        }
    }

    private void applyIngredients(ShapedRecipe recipe, ConfigManager ingredients, String toolName) {
        for (String ingredientKey : ingredients.getKeys(false)) {
            String materialName = ingredients.getString(ingredientKey);
            if (materialName == null) continue;

            char key = ingredientKey.charAt(0);

            // Tag-based ingredient (e.g. "#planks")
            if (materialName.startsWith("#")) {
                Tag<Material> tag = TAG_MAP.get(materialName.toLowerCase());
                if (tag != null) {
                    recipe.setIngredient(key, new RecipeChoice.MaterialChoice(tag));
                } else {
                    plugin.getLogger().warning("Unknown tag '" + materialName + "' in recipe for " + toolName);
                }
                continue;
            }

            try {
                Material material = Material.valueOf(materialName);
                recipe.setIngredient(key, material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in recipe for " + toolName + ": " + materialName);
            }
        }
    }

    public void unregisterRecipes() {
        for (ToolType toolType : ToolType.values()) {
            if (toolType == ToolType.SICKLE) continue;
            String toolName = toolType.getConfigKey();
            NamespacedKey key = new NamespacedKey(plugin, toolName + "_recipe");
            plugin.getServer().removeRecipe(key);
        }

        // Unregister sickle recipes
        for (String tier : plugin.getConfigManager().getSickleTiers()) {
            NamespacedKey key = new NamespacedKey(plugin, "sickle_" + tier + "_recipe");
            plugin.getServer().removeRecipe(key);
        }
    }
}
