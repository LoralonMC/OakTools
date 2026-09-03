package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Turns a diamond Sickle into a netherite Sickle in the smithing table.
 *
 * <p>This deliberately is not a {@code SmithingTransformRecipe}. The recipe API
 * matches ingredients by material only, so the base ingredient would have to be
 * a {@code MaterialChoice} of DIAMOND_HOE — which also matches a plain vanilla
 * diamond hoe, and turned every netherite hoe upgrade into a sickle.
 *
 * <p>Instead the vanilla diamond-to-netherite upgrade is left to run, and only
 * its result is restamped when the item that went in was a sickle. Vanilla
 * therefore still decides whether the combination is valid and still carries
 * over enchantments and durability; a plain hoe is never touched.
 */
public class SmithingListener implements Listener {

    private static final String NETHERITE_TIER = "netherite";

    private final OakTools plugin;

    public SmithingListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!plugin.getConfigManager().isSickleEnabled()) {
            return;
        }

        String netheriteTier = resolveNetheriteTier();
        if (netheriteTier == null) {
            return;
        }

        // Only touch the upgrade vanilla already accepted, and only when it
        // produces the material the netherite sickle is built on.
        ItemStack result = event.getResult();
        if (result == null || result.getType() != plugin.getConfigManager().getSickleBaseMaterial(netheriteTier)) {
            return;
        }

        ItemStack base = event.getInventory().getInputEquipment();
        if (base == null || base.getType() == Material.AIR) {
            return;
        }

        if (plugin.getItemFactory().getToolType(base) != ToolType.SICKLE) {
            return;
        }

        ItemStack upgraded = result.clone();
        plugin.getItemFactory().applySickleIdentity(upgraded, netheriteTier);
        event.setResult(upgraded);

        plugin.debug("[Smithing Debug] Upgraded a sickle to the " + netheriteTier + " tier");
    }

    /**
     * The configured tier key for netherite, or null when the server removed
     * that tier (in which case the vanilla hoe upgrade is left alone).
     */
    private String resolveNetheriteTier() {
        for (String tier : plugin.getConfigManager().getSickleTiers()) {
            if (tier.equalsIgnoreCase(NETHERITE_TIER)) {
                return tier;
            }
        }
        return null;
    }
}
