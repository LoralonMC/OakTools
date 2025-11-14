package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Handles recipe discovery for OakTools recipes.
 * Grants recipes to players when they obtain required materials (vanilla behavior).
 */
public class RecipeDiscoveryListener implements Listener {

    private final OakTools plugin;

    public RecipeDiscoveryListener(OakTools plugin) {
        this.plugin = plugin;
    }

    /**
     * Check and grant recipes when a player picks up an item.
     * Schedule on next tick since the item isn't in inventory yet when this event fires.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Material pickedUpMaterial = event.getItem().getItemStack().getType();

        // Check on next tick after item is added to inventory
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            checkAndGrantRecipes(player, pickedUpMaterial);
        });
    }

    /**
     * Check and grant recipes when a player crafts an item.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Material craftedMaterial = event.getRecipe().getResult().getType();
        checkAndGrantRecipes(player, craftedMaterial);
    }

    /**
     * Check if the player should receive any recipes based on the material they obtained.
     *
     * @param player the player
     * @param material the material they obtained
     */
    private void checkAndGrantRecipes(Player player, Material material) {
        for (ToolType toolType : ToolType.values()) {
            String toolName = toolType.name().toLowerCase();

            // Check if recipe is enabled
            if (!plugin.getConfigManager().getConfig().getBoolean("tools." + toolName + ".recipe.enabled", true)) {
                continue;
            }

            NamespacedKey key = new NamespacedKey(plugin, toolName + "_recipe");

            // Skip if player already has this recipe
            if (player.hasDiscoveredRecipe(key)) {
                continue;
            }

            // Get recipe ingredients
            Set<Material> requiredMaterials = getRecipeIngredients(toolName);

            // If the material they picked up/crafted is used in this recipe, check if they should get it
            if (requiredMaterials.contains(material)) {
                // Check if player has all required materials
                if (playerHasAllMaterials(player, requiredMaterials)) {
                    player.discoverRecipe(key);
                }
            }
        }
    }

    /**
     * Get all unique materials used in a tool's recipe.
     *
     * @param toolName the tool name
     * @return set of required materials
     */
    private Set<Material> getRecipeIngredients(String toolName) {
        Set<Material> materials = new HashSet<>();

        ConfigurationSection ingredientsSection = plugin.getConfigManager().getConfig()
                .getConfigurationSection("tools." + toolName + ".recipe.ingredients");

        if (ingredientsSection != null) {
            for (String key : ingredientsSection.getKeys(false)) {
                String materialName = ingredientsSection.getString(key);
                if (materialName != null) {
                    try {
                        materials.add(Material.valueOf(materialName));
                    } catch (IllegalArgumentException ignored) {
                        // Invalid material, skip
                    }
                }
            }
        }

        return materials;
    }

    /**
     * Check if a player has all the required materials in their inventory.
     *
     * @param player the player
     * @param requiredMaterials the required materials
     * @return true if player has all materials
     */
    private boolean playerHasAllMaterials(Player player, Set<Material> requiredMaterials) {
        for (Material material : requiredMaterials) {
            if (!player.getInventory().contains(material)) {
                return false;
            }
        }
        return true;
    }
}
