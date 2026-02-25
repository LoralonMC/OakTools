package dev.oakheart.oaktools.services;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.util.Constants;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DurabilityService {

    private final OakTools plugin;

    public DurabilityService(OakTools plugin) {
        this.plugin = plugin;
    }

    public boolean damage(ItemStack item, Player player, int amount) {
        if (!plugin.getItemFactory().isTool(item)) {
            return false;
        }

        if (!shouldConsumeDurability(player)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        int unbreakingLevel = meta.getEnchantLevel(Enchantment.UNBREAKING);
        if (unbreakingLevel > 0) {
            if (ThreadLocalRandom.current().nextInt(unbreakingLevel + 1) != 0) {
                return false;
            }
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer currentDamage = pdc.get(Constants.DURABILITY, PersistentDataType.INTEGER);
        Integer maxDurability = pdc.get(Constants.MAX_DURABILITY, PersistentDataType.INTEGER);

        if (currentDamage == null || maxDurability == null) {
            return false;
        }

        int newDamage = currentDamage + amount;

        if (newDamage >= maxDurability) {
            breakTool(item, player);
            return true;
        }

        pdc.set(Constants.DURABILITY, PersistentDataType.INTEGER, newDamage);
        item.setItemMeta(meta);

        plugin.getItemFactory().syncVanillaDurability(item);

        // Check for low durability warning
        int warningThreshold = plugin.getConfigManager().getDurabilityWarningThreshold();
        if (warningThreshold > 0) {
            int remaining = maxDurability - newDamage;
            int thresholdValue = (int) Math.ceil(maxDurability * (warningThreshold / 100.0));

            if (remaining <= thresholdValue) {
                var toolType = plugin.getItemFactory().getToolType(item);
                if (toolType != null) {
                    plugin.getMessageManager().sendMessage(player, "tool-low-durability", Map.of(
                            "tool", toolType.getDisplayName(),
                            "remaining", String.valueOf(remaining),
                            "max", String.valueOf(maxDurability)));
                }
            }
        }

        return false;
    }

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

        return (currentDamage + amount) >= maxDurability;
    }

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

        int newDamage = Math.max(0, currentDamage - amount);
        pdc.set(Constants.DURABILITY, PersistentDataType.INTEGER, newDamage);
        item.setItemMeta(meta);

        plugin.getItemFactory().syncVanillaDurability(item);
    }

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

        plugin.getItemFactory().syncVanillaDurability(item);
    }

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

    public int getRemainingDurability(ItemStack item) {
        int current = getCurrentDamage(item);
        int max = getMaxDurability(item);

        if (current == -1 || max == -1) {
            return -1;
        }

        return max - current;
    }

    private void breakTool(ItemStack item, Player player) {
        // Send break message before removing
        var toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != null) {
            plugin.getMessageManager().sendMessage(player, "tool-broken",
                    Map.of("tool", toolType.getDisplayName()));
        }

        Sound breakSound = Sound.sound(Key.key("minecraft:entity.item.break"), Sound.Source.PLAYER, 1.0f, 1.0f);
        player.playSound(breakSound);

        item.setAmount(0);
    }

    private boolean shouldConsumeDurability(Player player) {
        return switch (player.getGameMode()) {
            case CREATIVE -> plugin.getConfigManager().isCreativeConsumeDurability();
            case ADVENTURE -> plugin.getConfigManager().isAdventureConsumeDurability();
            case SPECTATOR -> false;
            default -> true;
        };
    }
}
