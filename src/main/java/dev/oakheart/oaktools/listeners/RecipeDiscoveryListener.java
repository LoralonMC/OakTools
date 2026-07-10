package dev.oakheart.oaktools.listeners;

import dev.oakheart.config.ConfigManager;
import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class RecipeDiscoveryListener implements Listener {

    private final OakTools plugin;

    // Cached ingredient sets and recipe keys, rebuilt on reload
    private Map<ToolType, Set<Material>> cachedIngredients = new EnumMap<>(ToolType.class);
    private Map<ToolType, NamespacedKey> cachedRecipeKeys = new EnumMap<>(ToolType.class);
    // All materials that appear in any recipe (for fast early-exit)
    private Set<Material> allIngredientMaterials = EnumSet.noneOf(Material.class);

    public RecipeDiscoveryListener(OakTools plugin) {
        this.plugin = plugin;
        rebuildCache();
    }

    /**
     * Rebuild cached ingredient sets from config. Called on construction and after reload.
     */
    public void rebuildCache() {
        Map<ToolType, Set<Material>> ingredients = new EnumMap<>(ToolType.class);
        Map<ToolType, NamespacedKey> recipeKeys = new EnumMap<>(ToolType.class);
        Set<Material> allMaterials = EnumSet.noneOf(Material.class);

        for (ToolType toolType : ToolType.values()) {
            // Must match RecipeManager, which builds config paths and recipe keys
            // from getConfigKey() ("vein-miner") — name().toLowerCase() gives
            // "vein_miner", so that tool's recipe was never auto-discovered.
            String toolName = toolType.getConfigKey();

            if (!plugin.getConfigManager().getConfig().getBoolean("tools." + toolName + ".recipe.enabled", true)) {
                continue;
            }

            Set<Material> toolIngredients = loadRecipeIngredients(toolName);
            if (!toolIngredients.isEmpty()) {
                ingredients.put(toolType, toolIngredients);
                recipeKeys.put(toolType, new NamespacedKey(plugin, toolName + "_recipe"));
                allMaterials.addAll(toolIngredients);
            }
        }

        this.cachedIngredients = ingredients;
        this.cachedRecipeKeys = recipeKeys;
        this.allIngredientMaterials = allMaterials;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Material pickedUpMaterial = event.getItem().getItemStack().getType();

        if (!allIngredientMaterials.contains(pickedUpMaterial)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            checkAndGrantRecipes(player, pickedUpMaterial);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Material craftedMaterial = event.getRecipe().getResult().getType();

        if (!allIngredientMaterials.contains(craftedMaterial)) {
            return;
        }

        checkAndGrantRecipes(player, craftedMaterial);
    }

    private void checkAndGrantRecipes(Player player, Material material) {
        for (var entry : cachedIngredients.entrySet()) {
            NamespacedKey key = cachedRecipeKeys.get(entry.getKey());
            if (key == null) {
                continue;
            }

            if (player.hasDiscoveredRecipe(key)) {
                continue;
            }

            Set<Material> requiredMaterials = entry.getValue();
            if (requiredMaterials.contains(material)) {
                if (playerHasAllMaterials(player, requiredMaterials)) {
                    player.discoverRecipe(key);
                }
            }
        }
    }

    private Set<Material> loadRecipeIngredients(String toolName) {
        Set<Material> materials = EnumSet.noneOf(Material.class);

        ConfigManager ingredientsSection = plugin.getConfigManager().getConfig()
                .getSection("tools." + toolName + ".recipe.ingredients");

        if (ingredientsSection != null) {
            for (String key : ingredientsSection.getKeys(false)) {
                String materialName = ingredientsSection.getString(key);
                if (materialName != null) {
                    try {
                        materials.add(Material.valueOf(materialName));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        return materials;
    }

    private boolean playerHasAllMaterials(Player player, Set<Material> requiredMaterials) {
        for (Material material : requiredMaterials) {
            if (!player.getInventory().contains(material)) {
                return false;
            }
        }
        return true;
    }
}
