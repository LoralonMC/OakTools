package dev.oakheart.oaktools.items;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.FeedSource;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.Constants;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Factory for creating OakTools tool items.
 */
public class ItemFactory {

    private final OakTools plugin;

    public ItemFactory(OakTools plugin) {
        this.plugin = plugin;
    }

    /**
     * Create a new tool item with default settings.
     *
     * @param toolType the type of tool to create
     * @return the created ItemStack
     */
    public ItemStack createTool(ToolType toolType) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int maxDurability = config.getInt("tools." + toolType.name().toLowerCase() + ".durability.max", 250);
        return createTool(toolType, maxDurability);
    }

    /**
     * Create a new tool item with specific durability.
     *
     * @param toolType the type of tool to create
     * @param currentDurability the current durability (0 = full, max = broken)
     * @return the created ItemStack
     */
    public ItemStack createTool(ToolType toolType, int currentDurability) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String toolPath = "tools." + toolType.name().toLowerCase();

        // Get base material from config
        String baseMaterialName = config.getString(toolPath + ".base_material", "WARPED_FUNGUS_ON_A_STICK");
        Material baseMaterial;
        try {
            baseMaterial = Material.valueOf(baseMaterialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid base_material '" + baseMaterialName + "' for " + toolType + ", using WARPED_FUNGUS_ON_A_STICK");
            baseMaterial = Material.WARPED_FUNGUS_ON_A_STICK;
        }

        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Get max durability from config
        int maxDurability = config.getInt(toolPath + ".durability.max", 250);

        // Set PDC data
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Constants.TOOL_TYPE, PersistentDataType.STRING, toolType.name());
        pdc.set(Constants.DURABILITY, PersistentDataType.INTEGER, currentDurability);
        pdc.set(Constants.MAX_DURABILITY, PersistentDataType.INTEGER, maxDurability);

        // Set default feed source for Trowel
        if (toolType == ToolType.TROWEL) {
            pdc.set(Constants.FEED_SOURCE, PersistentDataType.STRING, FeedSource.HOTBAR.name());
        }

        // Apply vanilla durability bar (visual only)
        if (config.getBoolean(toolPath + ".durability.use_vanilla_damage_bar", true) && meta instanceof Damageable damageable) {
            int vanillaMaxDurability = baseMaterial.getMaxDurability();
            int vanillaDamage = calculateVanillaDamage(currentDurability, maxDurability, vanillaMaxDurability);
            damageable.setDamage(vanillaDamage);
        }

        item.setItemMeta(meta);

        // Apply model
        plugin.getModelProviderManager().applyModel(item, toolType);

        // Set initial display name and lore
        plugin.getDisplayService().setInitialDisplay(item, toolType);

        return item;
    }

    /**
     * Check if an item is an OakTools tool.
     * Uses PDC as source of truth since different tools may use different base materials.
     *
     * @param item the item to check
     * @return true if the item is an OakTools tool
     */
    public boolean isTool(ItemStack item) {
        if (item == null) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(Constants.TOOL_TYPE, PersistentDataType.STRING);
    }

    /**
     * Get the tool type of an item.
     *
     * @param item the item to check
     * @return the tool type, or null if not a tool
     */
    public ToolType getToolType(ItemStack item) {
        if (!isTool(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String typeString = meta.getPersistentDataContainer().get(Constants.TOOL_TYPE, PersistentDataType.STRING);
        return ToolType.fromString(typeString);
    }

    /**
     * Calculate vanilla damage value for durability bar display.
     * Maps custom durability to the base item's vanilla max durability.
     *
     * @param currentDamage current damage value
     * @param maxDurability maximum custom durability
     * @param vanillaMaxDurability the base item's vanilla max durability
     * @return the vanilla damage value
     */
    private int calculateVanillaDamage(int currentDamage, int maxDurability, int vanillaMaxDurability) {
        if (maxDurability <= 0 || vanillaMaxDurability <= 0) {
            return 0;
        }
        double ratio = (double) currentDamage / maxDurability;
        return (int) Math.round(ratio * vanillaMaxDurability);
    }

    /**
     * Update the vanilla durability bar to match custom durability.
     *
     * @param item the item to update
     */
    public void syncVanillaDurability(ItemStack item) {
        if (!isTool(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer currentDamage = pdc.get(Constants.DURABILITY, PersistentDataType.INTEGER);
        Integer maxDurability = pdc.get(Constants.MAX_DURABILITY, PersistentDataType.INTEGER);

        if (currentDamage != null && maxDurability != null) {
            int vanillaMaxDurability = item.getType().getMaxDurability();
            int vanillaDamage = calculateVanillaDamage(currentDamage, maxDurability, vanillaMaxDurability);
            damageable.setDamage(vanillaDamage);
            item.setItemMeta(meta);
        }
    }
}
