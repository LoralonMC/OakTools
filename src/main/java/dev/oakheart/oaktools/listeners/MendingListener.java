package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.util.Constants;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

    private record MendingState(int xpToBlock, int expiryTick) {}

    // Track which players are currently mending OakTools items to prevent XP gain
    private final Map<UUID, MendingState> activeMending = new HashMap<>();

    public MendingListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        MendingState state = activeMending.get(uuid);
        if (state == null) {
            return;
        }

        // Check if the mending state has expired
        if (Bukkit.getCurrentTick() > state.expiryTick()) {
            activeMending.remove(uuid);
            return;
        }

        int xpToBlock = state.xpToBlock();
        int xpGain = event.getAmount();

        if (xpGain <= xpToBlock) {
            // Block all of this XP gain
            event.setAmount(0);
            activeMending.put(uuid, new MendingState(xpToBlock - xpGain, state.expiryTick()));
        } else {
            // Block partial XP gain
            event.setAmount(xpGain - xpToBlock);
            activeMending.remove(uuid);
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

        // Track that this player is mending an OakTools item with tick-based expiry
        UUID uuid = player.getUniqueId();
        int currentTick = Bukkit.getCurrentTick();
        int expiryTick = currentTick + 5;
        MendingState existing = activeMending.get(uuid);
        int totalXpToBlock = xpToConsume + (existing != null && currentTick <= existing.expiryTick() ? existing.xpToBlock() : 0);
        activeMending.put(uuid, new MendingState(totalXpToBlock, expiryTick));

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
    }

    /**
     * Clean up tracking data when a player disconnects to prevent memory leaks.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeMending.remove(event.getPlayer().getUniqueId());
    }
}
