package dev.oakheart.oaktools.services;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.FeedSource;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.model.WandMode;
import dev.oakheart.oaktools.util.Constants;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class DisplayService {

    private final OakTools plugin;
    private final MiniMessage miniMessage;

    public DisplayService(OakTools plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void setInitialDisplay(ItemStack item, ToolType toolType) {
        if (!plugin.getItemFactory().isTool(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        if (!plugin.getConfigManager().isDisplayEnabled(toolType)) {
            return;
        }

        updateDisplayName(item, meta, toolType);
        updateLore(item, meta, toolType);

        item.setItemMeta(meta);
    }

    public void updateDisplay(ItemStack item) {
        if (!plugin.getItemFactory().isTool(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType == null) {
            return;
        }

        if (!plugin.getConfigManager().isDisplayEnabled(toolType)) {
            return;
        }

        // Refresh the styled name as well as the lore, so a tool renamed or
        // re-skinned in config is caught up by items already in circulation
        // instead of keeping whatever they were stamped with when crafted.
        // Safe against player renames: an anvil writes customName(), a different
        // field which displays over itemName().
        updateDisplayName(item, meta, toolType);
        updateLore(item, meta, toolType);

        item.setItemMeta(meta);

        refreshModel(item, toolType);
    }

    /**
     * Re-applies the configured item model.
     *
     * <p>Runs after {@code setItemMeta} because model providers operate on the
     * stack. An empty {@code model-id} is left alone rather than cleared: a tool
     * shipped without a model yet should keep the vanilla look.
     */
    private void refreshModel(ItemStack item, ToolType toolType) {
        String modelId = plugin.getConfigManager().getConfig()
                .getString("tools." + toolType.getConfigKey() + ".model-id", "");
        if (modelId == null || modelId.isEmpty()) {
            return;
        }
        plugin.getModelProviderManager().applyModel(item, modelId);
    }

    private void updateDisplayName(ItemStack item, ItemMeta meta, ToolType toolType) {
        String nameTemplate = plugin.getConfigManager().getDisplayNameTemplate(toolType);

        TagResolver[] resolvers = buildPlaceholderResolvers(item);
        Component nameComponent = miniMessage.deserialize(nameTemplate, resolvers)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);

        meta.itemName(nameComponent);
    }

    private void updateLore(ItemStack item, ItemMeta meta, ToolType toolType) {
        List<String> loreTemplate = plugin.getConfigManager().getDisplayLoreTemplate(toolType);

        TagResolver[] resolvers = buildPlaceholderResolvers(item);

        List<Component> loreComponents = new ArrayList<>();
        for (String line : loreTemplate) {
            Component lineComponent = miniMessage.deserialize(line, resolvers)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
            loreComponents.add(lineComponent);
        }

        meta.lore(loreComponents);
    }

    private TagResolver[] buildPlaceholderResolvers(ItemStack item) {
        List<TagResolver> resolvers = new ArrayList<>();

        if (!plugin.getItemFactory().isTool(item)) {
            return resolvers.toArray(TagResolver[]::new);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return resolvers.toArray(TagResolver[]::new);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Integer currentDamage = pdc.get(Constants.DURABILITY, PersistentDataType.INTEGER);
        Integer maxDurability = pdc.get(Constants.MAX_DURABILITY, PersistentDataType.INTEGER);

        if (currentDamage != null && maxDurability != null) {
            int remaining = maxDurability - currentDamage;
            int percent = (int) Math.round(((double) remaining / maxDurability) * 100);

            resolvers.add(Placeholder.unparsed("cur", String.valueOf(currentDamage)));
            resolvers.add(Placeholder.unparsed("max", String.valueOf(maxDurability)));
            resolvers.add(Placeholder.unparsed("remaining", String.valueOf(remaining)));
            resolvers.add(Placeholder.unparsed("percent", String.valueOf(percent)));
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != null) {
            resolvers.add(Placeholder.unparsed("tool", toolType.getDisplayName()));
        }

        String feedSourceString = pdc.get(Constants.FEED_SOURCE, PersistentDataType.STRING);
        if (feedSourceString != null) {
            FeedSource feedSource = FeedSource.fromString(feedSourceString);
            resolvers.add(Placeholder.unparsed("feed_source", getFeedSourceDisplayName(feedSource)));
        }

        String wandModeString = pdc.get(Constants.WAND_MODE, PersistentDataType.STRING);
        if (wandModeString != null) {
            WandMode wandMode = WandMode.fromString(wandModeString);
            resolvers.add(Placeholder.unparsed("wand_mode", getWandModeDisplayName(wandMode)));
        }

        return resolvers.toArray(TagResolver[]::new);
    }

    public String getFeedSourceDisplayName(FeedSource feedSource) {
        return plugin.getConfigManager().getFeedSourceDisplayName(feedSource);
    }

    public String getWandModeDisplayName(WandMode wandMode) {
        return plugin.getConfigManager().getWandModeDisplayName(wandMode);
    }

    public Component parse(String text) {
        return miniMessage.deserialize(text);
    }

    public Component parseWithPlaceholders(String text, ItemStack item) {
        TagResolver[] resolvers = buildPlaceholderResolvers(item);
        return miniMessage.deserialize(text, resolvers);
    }
}
