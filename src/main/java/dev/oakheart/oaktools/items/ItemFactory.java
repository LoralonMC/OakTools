package dev.oakheart.oaktools.items;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.FeedSource;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.model.WandMode;
import dev.oakheart.oaktools.util.Constants;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ItemFactory {

    private final OakTools plugin;

    public ItemFactory(OakTools plugin) {
        this.plugin = plugin;
    }

    public ItemStack createTool(ToolType toolType) {
        return createTool(toolType, 0);
    }

    public ItemStack createTool(ToolType toolType, int damage) {
        Material baseMaterial = plugin.getConfigManager().getBaseMaterial(toolType);

        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        int maxDurability = plugin.getConfigManager().getMaxDurability(toolType);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Constants.TOOL_TYPE, PersistentDataType.STRING, toolType.name());
        pdc.set(Constants.DURABILITY, PersistentDataType.INTEGER, damage);
        pdc.set(Constants.MAX_DURABILITY, PersistentDataType.INTEGER, maxDurability);

        if (toolType == ToolType.TROWEL) {
            pdc.set(Constants.FEED_SOURCE, PersistentDataType.STRING, FeedSource.HOTBAR.name());
        }

        if (toolType == ToolType.WAND) {
            pdc.set(Constants.WAND_MODE, PersistentDataType.STRING, WandMode.FACE.name());
        }

        // Override tool component for harvesting tools so any base material can mine all blocks
        applyToolComponent(meta, toolType);

        if (plugin.getConfigManager().isUseVanillaDamageBar(toolType) && meta instanceof Damageable damageable) {
            int vanillaMaxDurability = baseMaterial.getMaxDurability();
            int vanillaDamage = calculateVanillaDamage(damage, maxDurability, vanillaMaxDurability);
            damageable.setDamage(vanillaDamage);
        }

        item.setItemMeta(meta);

        plugin.getModelProviderManager().applyModel(item, toolType);

        plugin.getDisplayService().setInitialDisplay(item, toolType);

        return item;
    }

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
     * Overrides the tool component on harvesting tools to set custom mining speed
     * and harvest level independently from the base material.
     * Also sets damagePerBlock=0 since we handle durability via PDC.
     */
    private void applyToolComponent(ItemMeta meta, ToolType toolType) {
        Tag<Material> mineableTag = switch (toolType) {
            case EXCAVATOR -> Tag.MINEABLE_SHOVEL;
            case LUMBERJACK -> Tag.MINEABLE_AXE;
            case VEIN_MINER -> Tag.MINEABLE_PICKAXE;
            default -> null;
        };

        if (mineableTag == null) return;

        ToolComponent tool = meta.getTool();
        if (tool == null) return;

        tool.setDamagePerBlock(0);

        float configuredSpeed = plugin.getConfigManager().getMiningSpeed(toolType);
        String harvestLevel = plugin.getConfigManager().getHarvestLevel(toolType);

        // Skip if both are set to "use base material defaults"
        if (configuredSpeed <= 0 && "none".equalsIgnoreCase(harvestLevel)) {
            meta.setTool(tool);
            return;
        }

        // Speed: null = use base material's natural speed, non-null = override
        Float speed = configuredSpeed > 0 ? configuredSpeed : null;

        // Base rule: all blocks in category are mineable with correct drops
        tool.addRule(mineableTag, speed, true);

        // Add harvest level restrictions (rules are evaluated in order, last match wins)
        switch (harvestLevel.toLowerCase()) {
            case "wood" -> {
                tool.addRule(Tag.NEEDS_STONE_TOOL, speed, false);
                tool.addRule(Tag.NEEDS_IRON_TOOL, speed, false);
                tool.addRule(Tag.NEEDS_DIAMOND_TOOL, speed, false);
            }
            case "stone" -> {
                tool.addRule(Tag.NEEDS_IRON_TOOL, speed, false);
                tool.addRule(Tag.NEEDS_DIAMOND_TOOL, speed, false);
            }
            case "iron" -> {
                tool.addRule(Tag.NEEDS_DIAMOND_TOOL, speed, false);
            }
            // diamond, netherite = no restrictions (base rule covers everything)
        }

        meta.setTool(tool);
    }

    private int calculateVanillaDamage(int currentDamage, int maxDurability, int vanillaMaxDurability) {
        if (maxDurability <= 0 || vanillaMaxDurability <= 0) {
            return 0;
        }
        double ratio = (double) currentDamage / maxDurability;
        return (int) Math.round(ratio * vanillaMaxDurability);
    }

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
