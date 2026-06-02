package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.events.FileUseEvent;
import dev.oakheart.oaktools.model.EditType;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.BlockUtil;
import dev.oakheart.oaktools.util.SoundUtil;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class FileListener implements Listener {

    private final OakTools plugin;

    public FileListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFileUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand();

        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.FILE) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clickedBlock = event.getClickedBlock();

            if (BlockUtil.isFlowerPot(clickedBlock)) {
                return;
            }

            if (clickedBlock.getState() instanceof org.bukkit.block.TileState) {
                return;
            }

            if (plugin.getConfigManager().getExcludedFileMaterials().contains(clickedBlock.getType())) {
                return;
            }

            if (plugin.getConfigManager().isProtectExtendedPistons() && BlockUtil.isProtectedPiston(clickedBlock)) {
                return;
            }
        }

        if (!player.hasPermission("oaktools.use.file")) {
            plugin.getMessageManager().send(player, "no-permission");
            event.setCancelled(true);
            return;
        }

        if (!plugin.getConfigManager().isFileEnabled()) {
            return;
        }

        if (!plugin.getConfigManager().isGamemodeAllowed(player.getGameMode())) {
            plugin.getMessageManager().send(player, "gamemode-denied");
            event.setCancelled(true);
            return;
        }

        if (!plugin.getConfigManager().isWorldAllowed(player.getWorld().getName())) {
            plugin.getMessageManager().send(player, "world-denied");
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handleRightClick(event, player, item, hand);
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
        }
    }

    private void handleRightClick(PlayerInteractEvent event, Player player, ItemStack item, EquipmentSlot hand) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        plugin.debug("[File Debug] Player " + player.getName() + " used File on block at " +
            block.getX() + "," + block.getY() + "," + block.getZ() + " (" + block.getType() + ")");
        plugin.debug("[File Debug] Event hand: " + hand + ", Item: " + (item != null ? item.getType() : "null"));

        org.bukkit.util.Vector interactionPoint = event.getInteractionPoint() != null ?
                event.getInteractionPoint().toVector() : null;

        boolean isSneaking = player.isSneaking();

        // Determine edit type (single check replaces the old double-pass)
        EditType editType = determineEditType(block);

        if (editType == null) {
            plugin.debug("[File Debug] No change — block type not supported or feature disabled");
            event.setCancelled(true);
            return;
        }

        // Protection check only for modifiable blocks
        if (!plugin.getProtectionService().canModifyBlock(player, block, hand, item)) {
            plugin.debug("[File Debug] Blocked: Protection denied");
            plugin.getMessageManager().send(player, "protection-denied");
            event.setCancelled(true);
            return;
        }

        // Perform the edit
        BlockData oldData = block.getBlockData().clone();
        boolean changed = false;

        switch (editType) {
            case MULTIPLE_FACING -> {
                plugin.debug("[File Debug] Feature:MultipleFacing (fence/glass pane/iron bars)");
                changed = BlockUtil.cycleMultipleFacing(block, event.getBlockFace(), interactionPoint, player.getFacing());
            }
            case WALL -> {
                plugin.debug("[File Debug] Feature:Wall");
                changed = BlockUtil.cycleWall(block, event.getBlockFace(), interactionPoint, player.getFacing());
            }
            case STAIRS -> {
                plugin.debug("[File Debug] Feature:Stairs " + (isSneaking ? "(toggle half)" : "(shape)"));
                if (isSneaking) {
                    changed = BlockUtil.toggleStairsHalf(block);
                } else {
                    changed = BlockUtil.editStairsShape(block, event.getBlockFace(), interactionPoint, player.getLocation().toVector(), plugin.getConfigManager().isDebug() ? plugin.getLogger() : null);
                }
            }
            case DIRECTIONAL -> {
                plugin.debug("[File Debug] Feature:Directional (observer/piston/etc)");
                changed = BlockUtil.rotateDirectional(block);
            }
            case AXIS -> {
                plugin.debug("[File Debug] Feature:Axis (log/pillar)");
                changed = BlockUtil.rotateAxis(block);
            }
            case SLAB -> {
                plugin.debug("[File Debug] Feature:Slab (toggle top/bottom)");
                changed = BlockUtil.toggleSlab(block);
            }
        }

        event.setCancelled(true);

        if (changed) {
            BlockData newData = block.getBlockData();
            plugin.debug("[File Debug] Edit successful!");
            plugin.debug("[File Debug] Old data: " + oldData.getAsString());
            plugin.debug("[File Debug] New data: " + newData.getAsString());
            handleSuccessfulEdit(event, player, item, hand, block, oldData, newData, editType);
        } else {
            plugin.debug("[File Debug] No change — block type not supported or feature disabled");
        }
    }

    /**
     * Determine what type of edit can be performed on this block.
     * Returns null if the block is not modifiable or the feature is disabled.
     */
    private EditType determineEditType(Block block) {
        var configManager = plugin.getConfigManager();
        if (configManager.isFeatureMultipleFacing() && BlockUtil.hasMultipleFacing(block)) {
            return EditType.MULTIPLE_FACING;
        }
        if (configManager.isFeatureWalls() && BlockUtil.isWall(block)) {
            return EditType.WALL;
        }
        if (configManager.isFeatureStairs() && BlockUtil.isStairs(block)) {
            return EditType.STAIRS;
        }
        if (configManager.isFeatureDirectional() && BlockUtil.isDirectional(block)) {
            return EditType.DIRECTIONAL;
        }
        if (configManager.isFeatureAxisRotation() && BlockUtil.hasAxis(block)) {
            return EditType.AXIS;
        }
        if (configManager.isFeatureSlabs() && BlockUtil.isSlab(block)) {
            return EditType.SLAB;
        }
        return null;
    }

    private void handleSuccessfulEdit(PlayerInteractEvent event, Player player, ItemStack item,
                                       EquipmentSlot hand, Block block, BlockData oldData,
                                       BlockData newData, EditType editType) {
        FileUseEvent fileEvent = new FileUseEvent(
                player, block, oldData, newData, item,
                event.getBlockFace(), hand, editType
        );
        plugin.getServer().getPluginManager().callEvent(fileEvent);

        if (fileEvent.isCancelled()) {
            plugin.debug("[File Debug] FileUseEvent was cancelled by another plugin, reverting change");
            block.setBlockData(oldData, false);
            return;
        }

        boolean broken = plugin.getDurabilityService().damage(item, player, 1);
        if (!broken) {
            plugin.getDisplayService().updateDisplay(item);
        }

        SoundUtil.playPlaceSound(block, newData, plugin);

        plugin.getCoreProtectLogger().logFileEdit(player, block, oldData, newData);
    }

}
