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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFileUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand();

        // Validate tool first
        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.FILE) {
            return;
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

        // Don't allow File tool on tile entities (blocks with complex persistent data)
        // This includes: chests, signs, spawners, skulls, banners, note blocks, beehives, etc.
        // TileEntity check catches all blocks with NBT data that shouldn't be rotated/edited
        if (block.getState() instanceof org.bukkit.block.TileState) {
            // Silently ignore and allow vanilla behavior (opening GUIs, etc.)
            if (isDebugEnabled()) {
                plugin.getLogger().info("[File Debug] Blocked: Block is a TileState (tile entity), allowing vanilla behavior");
            }
            // DON'T cancel - allow player to open chests, furnaces, etc.
            return;
        }

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
}
