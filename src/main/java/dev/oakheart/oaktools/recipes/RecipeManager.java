package dev.oakheart.oaktools.recipes;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.List;

public class RecipeManager {

    private final OakTools plugin;

    public RecipeManager(OakTools plugin) {
        this.plugin = plugin;
    }

    public void registerRecipes() {
        registerRecipe(ToolType.FILE);
        registerRecipe(ToolType.TROWEL);
        registerRecipe(ToolType.WAND);
    }

    private void registerRecipe(ToolType toolType) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String toolName = toolType.name().toLowerCase();

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

        ConfigurationSection ingredientsSection = config.getConfigurationSection(recipePath + ".ingredients");
        if (ingredientsSection == null) {
            plugin.getLogger().warning("Missing ingredients for " + toolName);
            return;
        }

        ItemStack result = plugin.getItemFactory().createTool(toolType, 0);

        NamespacedKey key = new NamespacedKey(plugin, toolName + "_recipe");
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape);

        String categoryString = config.getString(recipePath + ".category", "EQUIPMENT");
        try {
            org.bukkit.inventory.recipe.CraftingBookCategory category =
                org.bukkit.inventory.recipe.CraftingBookCategory.valueOf(categoryString);
            recipe.setCategory(category);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid recipe category for " + toolName + ": " + categoryString + ", using EQUIPMENT");
            recipe.setCategory(org.bukkit.inventory.recipe.CraftingBookCategory.EQUIPMENT);
        }

        for (String ingredientKey : ingredientsSection.getKeys(false)) {
            String materialName = ingredientsSection.getString(ingredientKey);
            if (materialName == null) {
                continue;
            }

            try {
                Material material = Material.valueOf(materialName);
                recipe.setIngredient(ingredientKey.charAt(0), material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in recipe for " + toolName + ": " + materialName);
            }
        }

        try {
            plugin.getServer().addRecipe(recipe);
            plugin.getLogger().info("Registered recipe for " + toolType.getDisplayName());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register recipe for " + toolName + ": " + e.getMessage());
        }
    }

    public void unregisterRecipes() {
        for (ToolType toolType : ToolType.values()) {
            String toolName = toolType.name().toLowerCase();
            NamespacedKey key = new NamespacedKey(plugin, toolName + "_recipe");
            plugin.getServer().removeRecipe(key);
        }
    }
}
