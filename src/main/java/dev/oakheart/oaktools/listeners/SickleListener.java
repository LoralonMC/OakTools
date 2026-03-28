package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles the Sickle's crop harvesting and auto-replant on BlockBreakEvent.
 * When a player breaks a mature crop while holding a sickle, surrounding
 * mature crops of the same type are harvested, replanted, and drops fall naturally.
 */
public class SickleListener implements Listener {

    private static final Map<Material, Material> SEED_MAP = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS,
            Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES,
            Material.TORCHFLOWER_CROP, Material.TORCHFLOWER_SEEDS,
            Material.PITCHER_CROP, Material.PITCHER_POD
    );

    private final OakTools plugin;

    public SickleListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!plugin.getItemFactory().isTool(item)) return;
        if (plugin.getItemFactory().getToolType(item) != ToolType.SICKLE) return;

        if (!plugin.getConfigManager().isSickleEnabled()) return;
        if (!player.hasPermission("oaktools.use.sickle")) return;
        if (!plugin.getConfigManager().isGamemodeAllowed(player.getGameMode())) return;
        if (!plugin.getConfigManager().isWorldAllowed(player.getWorld().getName())) return;

        Block brokenBlock = event.getBlock();
        if (!(brokenBlock.getBlockData() instanceof Ageable ageable)) return;
        if (ageable.getAge() < ageable.getMaximumAge()) return; // Not mature

        if (!SEED_MAP.containsKey(brokenBlock.getType())) return; // Not a supported crop

        String tier = plugin.getItemFactory().getToolTier(item);
        if (tier == null) return;
        int radius = plugin.getConfigManager().getSickleRadius(tier);

        // Cancel vanilla break — we handle everything (break, drops, replant, durability)
        event.setCancelled(true);

        // Collect crops to harvest (any mature supported crop in area, not just same type)
        List<Block> crops = new ArrayList<>();
        crops.add(brokenBlock);

        if (!player.isSneaking() && radius > 0) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Block candidate = brokenBlock.getRelative(dx, 0, dz);
                    if (!SEED_MAP.containsKey(candidate.getType())) continue;
                    if (!(candidate.getBlockData() instanceof Ageable candidateAge)) continue;
                    if (candidateAge.getAge() < candidateAge.getMaximumAge()) continue;
                    crops.add(candidate);
                }
            }
        }

        int unbreakingLevel = item.getEnchantmentLevel(Enchantment.UNBREAKING);
        int harvestedCount = 0;

        for (Block crop : crops) {
            Material seedMaterial = SEED_MAP.get(crop.getType());

            // Calculate drops using the player's actual sickle (respects Fortune)
            Collection<ItemStack> drops = crop.getDrops(item, player);

            // Remove one seed for replanting
            boolean seedConsumed = removeSeed(drops, seedMaterial);

            // CoreProtect logging (before modifying the block)
            plugin.getCoreProtectLogger().logHarvestingBreak(player, crop, crop.getBlockData());

            // Replant the crop at growth stage 0, or clear if no seed
            if (seedConsumed && crop.getBlockData() instanceof Ageable cropData) {
                cropData.setAge(0);
                crop.setBlockData(cropData);
            } else {
                crop.setType(Material.AIR);
            }

            // Drop remaining items naturally at the crop's location
            // Tag items so OakOverflow doesn't intercept them (sickle drops are meant to stay on ground)
            for (ItemStack drop : drops) {
                crop.getWorld().dropItemNaturally(crop.getLocation().add(0.5, 0.3, 0.5), drop, entity ->
                        entity.addScoreboardTag("oaktools_sickle_drop"));
            }

            harvestedCount++;

            // Durability damage for each crop (respects Unbreaking)
            if (shouldConsumeDurability(unbreakingLevel)) {
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof Damageable damageable) {
                    int newDamage = damageable.getDamage() + 1;
                    if (newDamage >= item.getType().getMaxDurability()) {
                        player.getInventory().setItemInMainHand(null);
                        player.getWorld().playSound(player.getLocation(),
                                org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                        break;
                    }
                    damageable.setDamage(newDamage);
                    item.setItemMeta(meta);
                }
            }
        }

        // Send harvest message
        if (harvestedCount > 1) {
            plugin.getMessageManager().sendMessage(player, "sickle-harvested",
                    Map.of("count", String.valueOf(harvestedCount)));
        }
    }

    /**
     * Removes one seed of the given material from the drops collection.
     * Returns true if a seed was found and consumed.
     */
    private boolean removeSeed(Collection<ItemStack> drops, Material seedMaterial) {
        Iterator<ItemStack> it = drops.iterator();
        while (it.hasNext()) {
            ItemStack drop = it.next();
            if (drop.getType() == seedMaterial) {
                if (drop.getAmount() > 1) {
                    drop.setAmount(drop.getAmount() - 1);
                } else {
                    it.remove();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Rolls the Unbreaking chance. Returns true if durability should be consumed.
     * Formula: 1 / (unbreakingLevel + 1) chance of consuming.
     */
    private boolean shouldConsumeDurability(int unbreakingLevel) {
        if (unbreakingLevel <= 0) return true;
        return ThreadLocalRandom.current().nextInt(unbreakingLevel + 1) == 0;
    }
}
