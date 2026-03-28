package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;

/**
 * Prevents vanilla durability damage on OakTools items.
 * Our custom DurabilityService handles all tool damage via PDC —
 * this listener blocks the vanilla system from interfering.
 * Only relevant for tools with vanilla tool base materials (e.g. NETHERITE_SHOVEL).
 */
public class ItemDamageListener implements Listener {

    private final OakTools plugin;

    public ItemDamageListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (plugin.getItemFactory().isTool(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
