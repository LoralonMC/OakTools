package dev.oakheart.oaktools.services;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.FeedSource;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.Constants;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles tool display names, lore, and MiniMessage formatting with placeholders.
 */
public class DisplayService {

    private final OakTools plugin;
    private final MiniMessage miniMessage;

    public DisplayService(OakTools plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Set the initial display name and lore when creating a new tool.
     * This sets the name once - after creation, players can rename freely.
     *
     * @param item the tool item
     * @param toolType the tool type
     */
    public void setInitialDisplay(ItemStack item, ToolType toolType) {
        if (!plugin.getItemFactory().isTool(item)) {
            return;
        }

        FileConfiguration config = plugin.getConfigManager().getConfig();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Check if display is enabled for this specific tool
        String toolPath = "tools." + toolType.name().toLowerCase() + ".display";
        if (!config.getBoolean(toolPath + ".enabled", true)) {
            return;
        }

        // Set initial display name and lore
        updateDisplayName(item, meta, toolType);
        updateLore(item, meta, toolType);

        item.setItemMeta(meta);
    }

    /**
     * Update the display name and lore of a tool.
     * Only updates lore - names are set once on creation and never changed.
     *
     * @param item the tool item
     */
    public void updateDisplay(ItemStack item) {
        if (!plugin.getItemFactory().isTool(item)) {
            return;
        }

        FileConfiguration config = plugin.getConfigManager().getConfig();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType == null) {
            return;
        }

        // Check if display is enabled for this specific tool
        String toolPath = "tools." + toolType.name().toLowerCase() + ".display";
        if (!config.getBoolean(toolPath + ".enabled", true)) {
            return;
        }

        // Never update display name - set once on creation, players can rename freely
        // Always update lore (contains dynamic info like feed source, durability placeholders)
        updateLore(item, meta, toolType);

        item.setItemMeta(meta);
    }

    /**
     * Update the display name of a tool.
     *
     * @param item the tool item
     * @param meta the item meta
     * @param toolType the tool type
     */
    private void updateDisplayName(ItemStack item, ItemMeta meta, ToolType toolType) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String path = "tools." + toolType.name().toLowerCase() + ".display.name";
        String nameTemplate = config.getString(path, "<white>" + toolType.getDisplayName() + "</white>");

        String nameWithPlaceholders = replacePlaceholders(nameTemplate, item);
        Component nameComponent = miniMessage.deserialize(nameWithPlaceholders)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);

        meta.displayName(nameComponent);
    }

    /**
     * Update the lore of a tool.
     *
     * @param item the tool item
     * @param meta the item meta
     * @param toolType the tool type
     */
    private void updateLore(ItemStack item, ItemMeta meta, ToolType toolType) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String path = "tools." + toolType.name().toLowerCase() + ".display.lore";
        List<String> loreTemplate = config.getStringList(path);

        List<Component> loreComponents = new ArrayList<>();
        for (String line : loreTemplate) {
            String lineWithPlaceholders = replacePlaceholders(line, item);
            Component lineComponent = miniMessage.deserialize(lineWithPlaceholders)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
            loreComponents.add(lineComponent);
        }

        meta.lore(loreComponents);
    }

    /**
     * Replace placeholders in a string with tool data.
     *
     * @param text the text with placeholders
     * @param item the tool item
     * @return the text with placeholders replaced
     */
    private String replacePlaceholders(String text, ItemStack item) {
        if (!plugin.getItemFactory().isTool(item)) {
            return text;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return text;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Get durability data
        Integer currentDamage = pdc.get(Constants.DURABILITY, PersistentDataType.INTEGER);
        Integer maxDurability = pdc.get(Constants.MAX_DURABILITY, PersistentDataType.INTEGER);

        if (currentDamage != null && maxDurability != null) {
            int remaining = maxDurability - currentDamage;
            int percent = (int) Math.round(((double) remaining / maxDurability) * 100);

            text = text.replace("%cur%", String.valueOf(currentDamage));
            text = text.replace("%max%", String.valueOf(maxDurability));
            text = text.replace("%remaining%", String.valueOf(remaining));
            text = text.replace("%percent%", String.valueOf(percent));
        }

        // Get tool type
        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != null) {
            text = text.replace("%tool%", toolType.getDisplayName());
        }

        // Get feed source (Trowel only)
        String feedSourceString = pdc.get(Constants.FEED_SOURCE, PersistentDataType.STRING);
        if (feedSourceString != null) {
            FeedSource feedSource = FeedSource.fromString(feedSourceString);
            text = text.replace("%feed_source%", getFeedSourceDisplayName(feedSource));
        }

        return text;
    }

    /**
     * Get the display name for a feed source from config.
     *
     * @param feedSource the feed source
     * @return the localized display name
     */
    public String getFeedSourceDisplayName(FeedSource feedSource) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String configKey = switch (feedSource) {
            case HOTBAR -> "messages.feed_sources.hotbar";
            case ROW_1 -> "messages.feed_sources.row_1";
            case ROW_2 -> "messages.feed_sources.row_2";
            case ROW_3 -> "messages.feed_sources.row_3";
        };

        // Get from config with fallback to default
        return config.getString(configKey, feedSource.getDisplayName());
    }

    /**
     * Parse a MiniMessage string into a Component.
     *
     * @param text the MiniMessage text
     * @return the Component
     */
    public Component parse(String text) {
        return miniMessage.deserialize(text);
    }

    /**
     * Parse a MiniMessage string with placeholders into a Component.
     *
     * @param text the MiniMessage text
     * @param item the tool item (for placeholders)
     * @return the Component
     */
    public Component parseWithPlaceholders(String text, ItemStack item) {
        String replaced = replacePlaceholders(text, item);
        return miniMessage.deserialize(replaced);
    }

}
