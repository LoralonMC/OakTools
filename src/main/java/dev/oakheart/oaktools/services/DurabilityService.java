package dev.oakheart.oaktools.services;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.Constants;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

/**
 * Handles tool durability consumption, mending, repair, and warnings.
 */
public class DurabilityService {

    private final OakTools plugin;
    private final Random random;

    public DurabilityService(OakTools plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    /**
     * Damage a tool, respecting Unbreaking enchantment.
     * Only damages in survival/adventure mode based on config.
     *
     * @param item the tool item
     * @param player the player using the tool
     * @param amount the damage amount
     * @return true if the tool broke, false otherwise
     */
    public boolean damage(ItemStack item, Player player, int amount) {
        if (!plugin.getItemFactory().isTool(item)) {
            return false;
        }

        // Check gamemode handling
        if (!shouldConsumeDurability(player)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // Check Unbreaking enchantment
        int unbreakingLevel = meta.getEnchantLevel(Enchantment.UNBREAKING);
        if (unbreakingLevel > 0) {
            // 1/(level+1) chance to consume durability
            if (random.nextInt(unbreakingLevel + 1) != 0) {
                return false; // Durability not consumed due to Unbreaking
            }
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer currentDamage = pdc.get(Constants.DURABILITY, PersistentDataType.INTEGER);
        Integer maxDurability = pdc.get(Constants.MAX_DURABILITY, PersistentDataType.INTEGER);

        if (currentDamage == null || maxDurability == null) {
            return false;
        }

        // Apply damage
        int newDamage = currentDamage + amount;

        // Check if tool broke
        if (newDamage >= maxDurability) {
            breakTool(item, player);
            return true;
        }

        // Update damage
        pdc.set(Constants.DURABILITY, PersistentDataType.INTEGER, newDamage);
        item.setItemMeta(meta);

        // Sync vanilla durability bar
        plugin.getItemFactory().syncVanillaDurability(item);

        return false;
    }

    /**
     * Check if using a tool would cause it to break.
     * Does NOT actually damage the tool - this is a read-only check.
     * Conservative: assumes Unbreaking enchantment does NOT proc.
     *
     * @param item the tool item
     * @param amount the damage amount to check
     * @return true if the tool would break, false otherwise
     */
    public boolean wouldBreak(ItemStack item, int amount) {
        if (!plugin.getItemFactory().isTool(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer currentDamage = pdc.get(Constants.DURABILITY, PersistentDataType.INTEGER);
        Integer maxDurability = pdc.get(Constants.MAX_DURABILITY, PersistentDataType.INTEGER);

        if (currentDamage == null || maxDurability == null) {
            return false;
        }

        // Check if damage would exceed max (conservative: assume Unbreaking doesn't proc)
        return (currentDamage + amount) >= maxDurability;
    }

    /**
     * Repair a tool by a specific amount.
     *
     * @param item the tool item
     * @param amount the repair amount
     */
    public void repair(ItemStack item, int amount) {
        if (!plugin.getItemFactory().isTool(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer currentDamage = pdc.get(Constants.DURABILITY, PersistentDataType.INTEGER);
        Integer maxDurability = pdc.get(Constants.MAX_DURABILITY, PersistentDataType.INTEGER);

        if (currentDamage == null || maxDurability == null) {
            return;
        }

        // Repair (capped at 0 damage = full durability)
        int newDamage = Math.max(0, currentDamage - amount);
        pdc.set(Constants.DURABILITY, PersistentDataType.INTEGER, newDamage);
        item.setItemMeta(meta);

        // Sync vanilla durability bar
        plugin.getItemFactory().syncVanillaDurability(item);
    }

    /**
     * Fully repair a tool to maximum durability.
     *
     * @param item the tool item
     */
    public void repairFully(ItemStack item) {
        if (!plugin.getItemFactory().isTool(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Constants.DURABILITY, PersistentDataType.INTEGER, 0);
        item.setItemMeta(meta);

        // Sync vanilla durability bar
        plugin.getItemFactory().syncVanillaDurability(item);
    }

    /**
     * Get the current damage of a tool.
     *
     * @param item the tool item
     * @return the current damage, or -1 if not a tool
     */
    public int getCurrentDamage(ItemStack item) {
        if (!plugin.getItemFactory().isTool(item)) {
            return -1;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return -1;
        }

        Integer damage = meta.getPersistentDataContainer().get(Constants.DURABILITY, PersistentDataType.INTEGER);
        return damage != null ? damage : -1;
    }

    /**
     * Get the maximum durability of a tool.
     *
     * @param item the tool item
     * @return the maximum durability, or -1 if not a tool
     */
    public int getMaxDurability(ItemStack item) {
        if (!plugin.getItemFactory().isTool(item)) {
            return -1;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return -1;
        }

        Integer maxDur = meta.getPersistentDataContainer().get(Constants.MAX_DURABILITY, PersistentDataType.INTEGER);
        return maxDur != null ? maxDur : -1;
    }

    /**
     * Get the remaining durability of a tool.
     *
     * @param item the tool item
     * @return the remaining durability, or -1 if not a tool
     */
    public int getRemainingDurability(ItemStack item) {
        int current = getCurrentDamage(item);
        int max = getMaxDurability(item);

        if (current == -1 || max == -1) {
            return -1;
        }

        return max - current;
    }

    /**
     * Break a tool and play the vanilla break sound.
     *
     * @param item the tool item
     * @param player the player
     */
    private void breakTool(ItemStack item, Player player) {
        // Play vanilla item break sound using Adventure API
        Sound breakSound = Sound.sound(Key.key("minecraft:entity.item.break"), Sound.Source.PLAYER, 1.0f, 1.0f);
        player.playSound(breakSound);

        // Remove item
        item.setAmount(0);
    }

    /**
     * Check if durability should be consumed based on player gamemode.
     *
     * @param player the player
     * @return true if durability should be consumed
     */
    private boolean shouldConsumeDurability(Player player) {
        GameMode mode = player.getGameMode();

        return switch (mode) {
            case CREATIVE -> plugin.getConfigManager().getConfig()
                    .getBoolean("general.restrictions.gamemode.creative.consume_durability", true);
            case ADVENTURE -> plugin.getConfigManager().getConfig()
                    .getBoolean("general.restrictions.gamemode.adventure.consume_durability", true);
            case SPECTATOR -> false; // Never consume in spectator
            default -> true; // Survival and other modes
        };
    }
}
