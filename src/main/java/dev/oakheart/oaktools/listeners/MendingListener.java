package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.util.Constants;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Mending enchantment for OakTools items with custom durability scaling.
 * Cancels vanilla Mending and applies custom repair logic based on configured max durability.
 */
public class MendingListener implements Listener {

    private final OakTools plugin;
    // Track which players are currently mending OakTools items to prevent XP gain
    private final Map<UUID, Integer> activeMending = new HashMap<>();

    public MendingListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if this player is currently mending an OakTools item
        if (activeMending.containsKey(uuid)) {
            int xpToBlock = activeMending.get(uuid);
            int xpGain = event.getAmount();

            if (xpGain <= xpToBlock) {
                // Block all of this XP gain
                event.setAmount(0);
                activeMending.put(uuid, xpToBlock - xpGain);
            } else {
                // Block partial XP gain
                event.setAmount(xpGain - xpToBlock);
                activeMending.remove(uuid);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMend(PlayerItemMendEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        // Check if this is an OakTools tool
        if (!plugin.getItemFactory().isTool(item)) {
            return; // Not our tool, let vanilla handle it
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Check if item has Mending enchantment
        if (!meta.hasEnchant(Enchantment.MENDING)) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer currentDamage = pdc.get(Constants.DURABILITY, PersistentDataType.INTEGER);
        Integer maxDurability = pdc.get(Constants.MAX_DURABILITY, PersistentDataType.INTEGER);

        if (currentDamage == null || maxDurability == null) {
            return;
        }

        // Check if tool needs repair
        if (currentDamage <= 0) {
            return; // Tool is already at full durability, let XP go to player
        }

        // Get the XP orb
        var orb = event.getExperienceOrb();
        int xpAmount = orb.getExperience();

        // Apply custom mending logic
        // Vanilla mending: 2 durability per 1 XP
        int repairAmount = xpAmount * 2; // Vanilla mending rate

        // Apply repair (capped at full durability)
        int newDamage = Math.max(0, currentDamage - repairAmount);
        int actualRepair = currentDamage - newDamage;

        // Calculate XP to consume (vanilla: 1 XP per 2 durability repaired)
        int xpToConsume = (int) Math.ceil((double) actualRepair / 2.0);
        xpToConsume = Math.min(xpToConsume, xpAmount); // Don't consume more than available

        // Track that this player is mending an OakTools item
        // This will be checked in PlayerExpChangeEvent to prevent XP gain
        UUID uuid = player.getUniqueId();
        int currentBlocked = activeMending.getOrDefault(uuid, 0);
        activeMending.put(uuid, currentBlocked + xpToConsume);

        // Update our custom durability
        pdc.set(Constants.DURABILITY, PersistentDataType.INTEGER, newDamage);
        item.setItemMeta(meta);

        // Sync to vanilla durability bar
        plugin.getItemFactory().syncVanillaDurability(item);

        // Update display (lore may show durability)
        plugin.getDisplayService().updateDisplay(item);

        // Consume XP from the orb
        int remainingXP = xpAmount - xpToConsume;
        if (remainingXP > 0) {
            orb.setExperience(remainingXP);
        } else {
            // No XP left, remove the orb completely
            orb.remove();
        }

        // Cancel the event to prevent vanilla from also processing it
        event.setCancelled(true);

        // Schedule cleanup of the activeMending tracker after a short delay
        // This ensures we don't block XP forever if something goes wrong
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            activeMending.remove(uuid);
        }, 5L);
    }
}
