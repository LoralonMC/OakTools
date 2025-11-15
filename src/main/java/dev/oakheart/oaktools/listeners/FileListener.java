package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.events.FileUseEvent;
import dev.oakheart.oaktools.model.EditType;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.BlockUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles File tool interactions for block state editing.
 */
public class FileListener implements Listener {

    private final OakTools plugin;

    public FileListener(OakTools plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if debug logging is enabled in config.
     */
    private boolean isDebugEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("general.debug", false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFileUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand();

        // Validate tool FIRST
        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return; // Not our tool, don't interfere at all
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.FILE) {
            return; // Not the File tool
        }

        // Now that we know it's our tool, check if clicking a block that should be ignored
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clickedBlock = event.getClickedBlock();

            // Explicit check for flower pots (backup to TileState check)
            if (isFlowerPot(clickedBlock)) {
                return; // Let vanilla handle flower pot interactions
            }

            // Check for TileStates (chests, signs, etc.)
            if (clickedBlock.getState() instanceof org.bukkit.block.TileState) {
                return; // Let vanilla handle it - don't process, don't cancel
            }

            // Check for excluded block types
            if (isBlockTypeExcluded(clickedBlock)) {
                return; // Let vanilla handle doors, levers, etc.
            }
        }

        // Check permission
        if (!player.hasPermission("oaktools.use.file")) {
            plugin.getMessageService().sendMessage(player, "no_permission");
            event.setCancelled(true);
            return;
        }

        // Check if tool is enabled
        if (!plugin.getConfigManager().getConfig().getBoolean("tools.file.enabled", true)) {
            return;
        }

        // Check gamemode
        if (!canUseInGamemode(player)) {
            return;
        }

        // Handle right-click only
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handleRightClick(event, player, item, hand);
        }
    }

    /**
     * Handle right-click with File.
     */
    private void handleRightClick(PlayerInteractEvent event, Player player, ItemStack item, EquipmentSlot hand) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (isDebugEnabled()) {
            plugin.getLogger().info("[File Debug] Player " + player.getName() + " used File on block at " +
                block.getX() + "," + block.getY() + "," + block.getZ() + " (" + block.getType() + ")");
            plugin.getLogger().info("[File Debug] Event hand: " + hand + ", Item: " + (item != null ? item.getType() : "null"));
        }

        // Note: TileState and exclusion checks are now done earlier in onFileUse()
        // to prevent any interference with vanilla interactions

        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Get interaction point for precise cursor-based detection
        org.bukkit.util.Vector interactionPoint = event.getInteractionPoint() != null ?
                event.getInteractionPoint().toVector() : null;

        // Check if sneaking - for stairs half toggle
        boolean isSneaking = player.isSneaking();

        // FIRST: Check if the block is even modifiable BEFORE doing protection checks
        // This prevents unnecessary fake BlockPlaceEvent calls on unmodifiable blocks
        boolean isModifiable = false;
        if (config.getBoolean("tools.file.features.multiple_facing", true) &&
            BlockUtil.hasMultipleFacing(block)) {
            isModifiable = true;
        } else if (config.getBoolean("tools.file.features.walls", true) &&
                   BlockUtil.isWall(block)) {
            isModifiable = true;
        } else if (config.getBoolean("tools.file.features.stairs", true) &&
                   BlockUtil.isStairs(block)) {
            isModifiable = true;
        } else if (config.getBoolean("tools.file.features.directional", true) &&
                   BlockUtil.isDirectional(block)) {
            isModifiable = true;
        } else if (config.getBoolean("tools.file.features.axis_rotation", true) &&
                   BlockUtil.hasAxis(block)) {
            isModifiable = true;
        } else if (config.getBoolean("tools.file.features.slabs", true) &&
                   BlockUtil.isSlab(block)) {
            isModifiable = true;
        }

        // If block is not modifiable, cancel event and return early
        if (!isModifiable) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] No change - block type not supported or feature disabled");
            }
            event.setCancelled(true);
            return;
        }

        // ONLY check protection if block is modifiable
        if (!plugin.getProtectionService().canModifyBlock(player, block, hand, item)) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] Blocked: Protection denied");
            }
            plugin.getMessageService().sendMessage(player, "protection_denied");
            event.setCancelled(true);
            return;
        }

        // Now actually modify the block
        BlockData oldData = block.getBlockData().clone();
        EditType editType = null;
        boolean changed = false;

        // Priority order: MultipleFacing -> Walls -> Stairs -> Directional -> Axis -> Slabs
        if (config.getBoolean("tools.file.features.multiple_facing", true) &&
            BlockUtil.hasMultipleFacing(block)) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] Feature:MultipleFacing (fence/glass pane/iron bars)");
            }
            changed = BlockUtil.cycleMultipleFacing(block, event.getBlockFace(), interactionPoint, player.getFacing());
            editType = EditType.MULTIPLE_FACING;
        } else if (config.getBoolean("tools.file.features.walls", true) &&
                   BlockUtil.isWall(block)) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] Feature:Wall");
            }
            changed = BlockUtil.cycleWall(block, event.getBlockFace(), interactionPoint, player.getFacing());
            editType = EditType.WALL;
        } else if (config.getBoolean("tools.file.features.stairs", true) &&
                   BlockUtil.isStairs(block)) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] Feature:Stairs " + (isSneaking ? "(toggle half)" : "(shape)"));
            }
            if (isSneaking) {
                // Sneak + right-click = toggle half (TOP/BOTTOM)
                changed = BlockUtil.toggleStairsHalf(block);
            } else {
                // Normal right-click = change shape based on cursor position
                changed = BlockUtil.editStairsShape(block, event.getBlockFace(), interactionPoint, player.getLocation().toVector(), isDebugEnabled());
            }
            editType = EditType.STAIRS;
        } else if (config.getBoolean("tools.file.features.directional", true) &&
                   BlockUtil.isDirectional(block)) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] Feature:Directional (observer/piston/etc)");
            }
            changed = BlockUtil.rotateDirectional(block);
            editType = EditType.DIRECTIONAL;
        } else if (config.getBoolean("tools.file.features.axis_rotation", true) &&
                   BlockUtil.hasAxis(block)) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] Feature:Axis (log/pillar)");
            }
            changed = BlockUtil.rotateAxis(block);
            editType = EditType.AXIS;
        } else if (config.getBoolean("tools.file.features.slabs", true) &&
                   BlockUtil.isSlab(block)) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] Feature:Slab (toggle top/bottom)");
            }
            changed = BlockUtil.toggleSlab(block);
            editType = EditType.SLAB;
        }

        // Always cancel the event to prevent vanilla behavior (placing blocks from offhand, etc.)
        event.setCancelled(true);

        if (changed) {
            BlockData newData = block.getBlockData();
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] Edit successful!");
                plugin.getLogger().info("[File Debug] Old data: " + oldData.getAsString());
                plugin.getLogger().info("[File Debug] New data: " + newData.getAsString());
            }
            handleSuccessfulEdit(event, player, item, hand, block, oldData, newData, editType);
        } else {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] No change - block type not supported or feature disabled");
            }
        }
    }


    /**
     * Handle successful block edit (event firing, durability, logging).
     * Note: Protection is already checked before this method is called.
     * Block has already been edited by BlockUtil methods.
     */
    @SuppressWarnings("removal") // Bukkit Sound enum is deprecated but needed to get material's sound
    private void handleSuccessfulEdit(PlayerInteractEvent event, Player player, ItemStack item,
                                       EquipmentSlot hand, Block block, BlockData oldData,
                                       BlockData newData, EditType editType) {
        // Fire custom event
        FileUseEvent fileEvent = new FileUseEvent(
                player, block, oldData, newData, item,
                event.getBlockFace(), hand, editType
        );
        plugin.getServer().getPluginManager().callEvent(fileEvent);

        if (fileEvent.isCancelled()) {
            // Revert the change (event already cancelled at the top)
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] FileUseEvent was cancelled by another plugin, reverting change");
            }
            block.setBlockData(oldData, false);
            return;
        }

        // Damage tool durability (may break tool, but edit already happened - vanilla behavior)
        plugin.getDurabilityService().damage(item, player, 1);

        // Update display (in case durability changed lore)
        plugin.getDisplayService().updateDisplay(item);

        // Play the block's place sound (vanilla behavior)
        // Use world.playSound so all nearby players hear it
        try {
            org.bukkit.Sound bukkitSound = newData.getMaterial().createBlockData().getSoundGroup().getPlaceSound();
            if (bukkitSound != null) {
                // Play using world.playSound so all nearby players hear it (vanilla behavior)
                block.getWorld().playSound(block.getLocation(), bukkitSound, org.bukkit.SoundCategory.BLOCKS, 1.0f, 0.8f);
                if (isDebugEnabled()) {
                    plugin.getLogger().info("[File Debug] Playing sound: " + bukkitSound.name() + " using world.playSound");
                }
            } else if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] No place sound for " + newData.getMaterial());
            }
        } catch (Exception e) {
            if (isDebugEnabled()) {
                plugin.getLogger().warning("[File Debug] Error playing sound: " + e.getMessage());
            }
        }

        // Log to CoreProtect
        plugin.getCoreProtectLogger().logFileEdit(player, block, oldData, newData);

        // Event already cancelled at the top of handleRightClick
    }

    /**
     * Check if player can use File in their current gamemode.
     */
    private boolean canUseInGamemode(Player player) {
        GameMode mode = player.getGameMode();
        FileConfiguration config = plugin.getConfigManager().getConfig();

        return switch (mode) {
            case CREATIVE -> config.getBoolean("general.restrictions.gamemode.creative.allow_use", true);
            case ADVENTURE -> config.getBoolean("general.restrictions.gamemode.adventure.allow_use", false);
            case SPECTATOR -> config.getBoolean("general.restrictions.gamemode.spectator.allow_use", false);
            default -> true;
        };
    }

    /**
     * Check if a block type should be excluded from File tool editing.
     * Excludes attachable blocks, multi-block structures, and special blocks.
     *
     * @param block the block to check
     * @return true if the block should be excluded
     */
    private boolean isBlockTypeExcluded(Block block) {
        String materialName = block.getType().name();

        // Exclude all torch types (wall torches, soul torches, redstone torches, etc.)
        if (materialName.contains("TORCH")) {
            return true;
        }

        // Exclude all door types (including trap doors)
        if (materialName.contains("DOOR")) {
            return true;
        }

        // Exclude all vine types
        if (materialName.contains("VINE")) {
            return true;
        }

        // Exclude mushroom blocks (can cause issues with block states)
        if (materialName.contains("MUSHROOM_BLOCK")) {
            return true;
        }

        // Exclude portals
        if (materialName.contains("PORTAL")) {
            return true;
        }

        // Exclude specific problematic blocks
        return switch (block.getType()) {
            case LEVER,
                 TRIPWIRE_HOOK,
                 END_PORTAL_FRAME,
                 LADDER,
                 // Exclude coral fans (wall decorations)
                 TUBE_CORAL_FAN, TUBE_CORAL_WALL_FAN,
                 BRAIN_CORAL_FAN, BRAIN_CORAL_WALL_FAN,
                 BUBBLE_CORAL_FAN, BUBBLE_CORAL_WALL_FAN,
                 FIRE_CORAL_FAN, FIRE_CORAL_WALL_FAN,
                 HORN_CORAL_FAN, HORN_CORAL_WALL_FAN,
                 DEAD_TUBE_CORAL_FAN, DEAD_TUBE_CORAL_WALL_FAN,
                 DEAD_BRAIN_CORAL_FAN, DEAD_BRAIN_CORAL_WALL_FAN,
                 DEAD_BUBBLE_CORAL_FAN, DEAD_BUBBLE_CORAL_WALL_FAN,
                 DEAD_FIRE_CORAL_FAN, DEAD_FIRE_CORAL_WALL_FAN,
                 DEAD_HORN_CORAL_FAN, DEAD_HORN_CORAL_WALL_FAN,
                 // Exclude glow lichen (decorative cave block)
                 GLOW_LICHEN,
                 // Exclude sculk veins (decorative cave block, has MultipleFacing)
                 SCULK_VEIN,
                 // Exclude amethyst buds and clusters (decorative cave blocks)
                 SMALL_AMETHYST_BUD, MEDIUM_AMETHYST_BUD, LARGE_AMETHYST_BUD, AMETHYST_CLUSTER,
                 // Exclude interactive blocks with GUIs
                 CRAFTING_TABLE,
                 LOOM, GRINDSTONE, STONECUTTER,
                 CARTOGRAPHY_TABLE, SMITHING_TABLE,
                 ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 ENCHANTING_TABLE,
                 // Exclude buttons (all types)
                 OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON, JUNGLE_BUTTON,
                 ACACIA_BUTTON, DARK_OAK_BUTTON, MANGROVE_BUTTON, CHERRY_BUTTON,
                 BAMBOO_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON,
                 STONE_BUTTON, POLISHED_BLACKSTONE_BUTTON,
                 // Exclude pressure plates (all types)
                 OAK_PRESSURE_PLATE, SPRUCE_PRESSURE_PLATE, BIRCH_PRESSURE_PLATE,
                 JUNGLE_PRESSURE_PLATE, ACACIA_PRESSURE_PLATE, DARK_OAK_PRESSURE_PLATE,
                 MANGROVE_PRESSURE_PLATE, CHERRY_PRESSURE_PLATE, BAMBOO_PRESSURE_PLATE,
                 CRIMSON_PRESSURE_PLATE, WARPED_PRESSURE_PLATE,
                 STONE_PRESSURE_PLATE, POLISHED_BLACKSTONE_PRESSURE_PLATE,
                 HEAVY_WEIGHTED_PRESSURE_PLATE, LIGHT_WEIGHTED_PRESSURE_PLATE,
                 // Exclude beds (multi-block structures)
                 WHITE_BED, ORANGE_BED, MAGENTA_BED, LIGHT_BLUE_BED, YELLOW_BED,
                 LIME_BED, PINK_BED, GRAY_BED, LIGHT_GRAY_BED, CYAN_BED,
                 PURPLE_BED, BLUE_BED, BROWN_BED, GREEN_BED, RED_BED, BLACK_BED,
                 // Exclude special/unique blocks
                 DRAGON_EGG,
                 BELL,
                 RESPAWN_ANCHOR,
                 // Exclude eggs and spawn blocks
                 TURTLE_EGG, SNIFFER_EGG, FROGSPAWN,
                 // Exclude cauldrons (water/lava/powder snow levels)
                 CAULDRON, WATER_CAULDRON, LAVA_CAULDRON, POWDER_SNOW_CAULDRON,
                 // Exclude cake variants (bite level/candles)
                 CAKE,
                 CANDLE_CAKE, WHITE_CANDLE_CAKE, ORANGE_CANDLE_CAKE, MAGENTA_CANDLE_CAKE,
                 LIGHT_BLUE_CANDLE_CAKE, YELLOW_CANDLE_CAKE, LIME_CANDLE_CAKE,
                 PINK_CANDLE_CAKE, GRAY_CANDLE_CAKE, LIGHT_GRAY_CANDLE_CAKE,
                 CYAN_CANDLE_CAKE, PURPLE_CANDLE_CAKE, BLUE_CANDLE_CAKE,
                 BROWN_CANDLE_CAKE, GREEN_CANDLE_CAKE, RED_CANDLE_CAKE, BLACK_CANDLE_CAKE,
                 // Exclude composter (fill level)
                 COMPOSTER,
                 // Exclude other attachable/special blocks
                 TRIPWIRE, END_PORTAL, NETHER_PORTAL,
                 REPEATER, COMPARATOR,
                 RAIL, POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL -> true;
            default -> false;
        };
    }

    /**
     * Check if a block is a flower pot (any variant).
     * Flower pots should use vanilla interaction (removing flowers).
     *
     * @param block the block to check
     * @return true if the block is a flower pot
     */
    private boolean isFlowerPot(Block block) {
        String materialName = block.getType().name();
        return materialName.equals("FLOWER_POT") || materialName.startsWith("POTTED_");
    }
}
