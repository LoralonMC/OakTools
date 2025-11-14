package dev.oakheart.oaktools.util;

import dev.oakheart.oaktools.model.FeedSource;
import org.bukkit.Material;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for inventory operations and feed source scanning.
 */
public class InventoryUtil {

    /**
     * Get all valid placeable blocks from a feed source.
     *
     * @param player the player
     * @param feedSource the feed source
     * @return list of valid placeable item stacks
     */
    public static List<ItemStack> getPlaceableBlocks(Player player, FeedSource feedSource) {
        List<ItemStack> placeableBlocks = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();

        for (int i = feedSource.getStartSlot(); i < feedSource.getEndSlot(); i++) {
            ItemStack item = inventory.getItem(i);

            if (item != null && isPlaceable(item)) {
                placeableBlocks.add(item);
            }
        }

        return placeableBlocks;
    }

    /**
     * Check if an item is placeable (valid single-block placeable item).
     * Excludes multi-block structures (doors, beds, tall plants, etc.) and complex blocks.
     *
     * @param item the item to check
     * @return true if placeable
     */
    public static boolean isPlaceable(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        Material material = item.getType();

        // Must be a block
        if (!material.isBlock()) {
            return false;
        }

        // Exclude items with custom plugin data (ExecutableItems, ItemsAdder, Nexo, etc.)
        // These items have PDC data that gets lost when placed, turning them into vanilla blocks
        if (item.hasItemMeta() && item.getItemMeta() != null) {
            var meta = item.getItemMeta();

            // Check for custom PDC keys (ExecutableItems, ItemsAdder, Nexo, etc.)
            // This catches most custom items/blocks/currencies
            if (!meta.getPersistentDataContainer().isEmpty()) {
                // Has custom plugin data - don't allow placement
                // This prevents ExecutableItems blocks from being placed and losing their data
                return false;
            }
        }

        // Get the block data to check properties
        var blockData = material.createBlockData();

        // Exclude multi-block items (doors, tall plants, etc.)
        // These implement Bisected interface (two-block-tall structures)
        // Note: Stairs also implement Bisected but are NOT multi-block, so we exclude them from this check
        if (blockData instanceof Bisected &&
            !(blockData instanceof org.bukkit.block.data.type.Stairs)) {
            return false;
        }

        // Exclude beds (multi-block horizontal structures)
        if (blockData instanceof Bed) {
            return false;
        }

        // Exclude signs (can have complex NBT data and wall variants)
        if (blockData instanceof Sign) {
            return false;
        }

        // Exclude banners (can have complex NBT patterns)
        if (material.name().contains("BANNER")) {
            return false;
        }

        // Exclude shulker boxes (all colors) - they can store items
        if (material.name().contains("SHULKER_BOX")) {
            return false;
        }

        // Exclude specific tile entities, interactive blocks, and special items
        switch (material) {
            // Tile entities with storage/complex data
            case CHEST, TRAPPED_CHEST, ENDER_CHEST,
                 FURNACE, BLAST_FURNACE, SMOKER,
                 BARREL, HOPPER, DROPPER, DISPENSER,
                 BREWING_STAND, JUKEBOX,
                 SPAWNER, BEACON, CONDUIT,
                 LECTERN, DECORATED_POT,
            // Interactive blocks with GUIs
                 CRAFTING_TABLE,
                 LOOM, GRINDSTONE, STONECUTTER,
                 CARTOGRAPHY_TABLE, SMITHING_TABLE,
                 ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 ENCHANTING_TABLE,
            // Unobtainable blocks (creative/commands only)
                 BARRIER,
                 LIGHT,
                 STRUCTURE_BLOCK, STRUCTURE_VOID,
                 COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                 JIGSAW,
                 BUDDING_AMETHYST,
                 REINFORCED_DEEPSLATE,
                 PETRIFIED_OAK_SLAB,
            // Special/unique blocks
                 DRAGON_EGG,
                 BELL,
                 RESPAWN_ANCHOR,
                 TNT,
            // Eggs and spawn blocks
                 TURTLE_EGG, SNIFFER_EGG, FROGSPAWN,
            // Cauldrons (water/lava/powder snow levels)
                 CAULDRON, WATER_CAULDRON, LAVA_CAULDRON, POWDER_SNOW_CAULDRON,
            // Cake variants (bite level/candles)
                 CAKE,
                 CANDLE_CAKE, WHITE_CANDLE_CAKE, ORANGE_CANDLE_CAKE, MAGENTA_CANDLE_CAKE,
                 LIGHT_BLUE_CANDLE_CAKE, YELLOW_CANDLE_CAKE, LIME_CANDLE_CAKE,
                 PINK_CANDLE_CAKE, GRAY_CANDLE_CAKE, LIGHT_GRAY_CANDLE_CAKE,
                 CYAN_CANDLE_CAKE, PURPLE_CANDLE_CAKE, BLUE_CANDLE_CAKE,
                 BROWN_CANDLE_CAKE, GREEN_CANDLE_CAKE, RED_CANDLE_CAKE, BLACK_CANDLE_CAKE,
            // Composter (fill level)
                 COMPOSTER:
                return false;
        }

        // Exclude air and replaceable blocks (these are targets, not placeable)
        if (!material.isSolid() && !material.name().contains("GLASS") &&
            !material.name().contains("FENCE") && !material.name().contains("PANE") &&
            !material.name().contains("STAIRS") && !material.name().contains("SLAB")) {
            // Allow glass, fences, stairs, and slabs even if they're not "solid"
            return false;
        }

        return true;
    }

    /**
     * Consume one item from a player's inventory.
     *
     * @param player the player
     * @param item the item to consume
     * @return true if successful
     */
    public static boolean consumeItem(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();

        // Find and consume the item
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot != null && slot.isSimilar(item)) {
                if (slot.getAmount() > 1) {
                    slot.setAmount(slot.getAmount() - 1);
                } else {
                    inventory.setItem(i, null);
                }
                return true;
            }
        }

        return false;
    }
}
