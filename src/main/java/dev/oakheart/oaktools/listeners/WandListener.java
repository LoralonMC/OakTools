package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.events.WandPlaceEvent;
import dev.oakheart.oaktools.managers.WandHistoryManager;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.model.WandMode;
import dev.oakheart.oaktools.util.Constants;
import dev.oakheart.oaktools.util.InventoryUtil;
import dev.oakheart.oaktools.util.SoundUtil;
import dev.oakheart.oaktools.util.WandPlacementCalculator;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.ArrayList;
import java.util.List;

public class WandListener implements Listener {

    private final OakTools plugin;
    private final WandHistoryManager historyManager;

    public WandListener(OakTools plugin, WandHistoryManager historyManager) {
        this.plugin = plugin;
        this.historyManager = historyManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onWandModeCycle(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.WAND) {
            return;
        }

        if (!player.isSneaking()) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!player.hasPermission("oaktools.use.wand")) {
            return;
        }

        if (!plugin.getConfigManager().isWandEnabled()) {
            return;
        }

        if (!plugin.getConfigManager().isWorldAllowed(player.getWorld().getName())) {
            plugin.getMessageManager().send(player, "world-denied");
            event.setCancelled(true);
            return;
        }

        cycleMode(player, item, event.getHand());
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onWandUndo(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.WAND) {
            return;
        }

        if (!player.hasPermission("oaktools.use.wand")) {
            return;
        }

        if (!plugin.getConfigManager().isWandEnabled()) {
            return;
        }

        if (!plugin.getConfigManager().isWandUndoEnabled()) {
            return;
        }

        event.setCancelled(true);

        int undoneCount = historyManager.undo(player);
        if (undoneCount > 0) {
            plugin.getMessageManager().send(player, "wand-undo",
                Placeholder.unparsed("count", String.valueOf(undoneCount)));
        } else {
            plugin.getMessageManager().send(player, "wand-undo-empty");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand();

        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.WAND) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clickedBlock = event.getClickedBlock();

            if (dev.oakheart.oaktools.util.BlockUtil.isFlowerPot(clickedBlock)) {
                return;
            }

            if (clickedBlock.getState() instanceof org.bukkit.block.TileState) {
                return;
            }

            if (dev.oakheart.oaktools.util.BlockUtil.isInteractiveBlock(clickedBlock)) {
                return;
            }
        }

        if (player.isSneaking()) {
            return;
        }

        if (!player.hasPermission("oaktools.use.wand")) {
            plugin.getMessageManager().send(player, "no-permission");
            event.setCancelled(true);
            return;
        }

        if (!plugin.getConfigManager().isWandEnabled()) {
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
            // Clear preview before placement
            if (plugin.getWandPreviewManager() != null) {
                plugin.getWandPreviewManager().clearPreviewForPlayer(player);
            }
            handleWandPlace(event, player, item, hand);
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onWandEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack item = player.getInventory().getItem(hand);

        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.WAND) {
            return;
        }

        if (player.isSneaking()) {
            cycleMode(player, item, hand);
            event.setCancelled(true);
        }
    }

    private void cycleMode(Player player, ItemStack item, EquipmentSlot hand) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String wandModeString = meta.getPersistentDataContainer()
                .get(Constants.WAND_MODE, PersistentDataType.STRING);
        WandMode currentMode = WandMode.fromString(wandModeString);

        WandMode nextMode = currentMode.next();

        meta.getPersistentDataContainer().set(Constants.WAND_MODE, PersistentDataType.STRING, nextMode.name());
        item.setItemMeta(meta);

        plugin.getDisplayService().updateDisplay(item);

        String modeName = plugin.getDisplayService().getWandModeDisplayName(nextMode);
        plugin.getMessageManager().send(player, "wand-mode-changed",
            Placeholder.unparsed("wand_mode", modeName));

        String soundName = plugin.getConfigManager().getWandModeSwitchSound();
        try {
            String keyString = soundName.toLowerCase().replace('_', '.');
            if (!keyString.contains(":")) {
                keyString = "minecraft:" + keyString;
            }
            Key soundKey = Key.key(keyString);
            Sound sound = Sound.sound(soundKey, Sound.Source.PLAYER, 1.0f, 1.0f);
            player.playSound(sound);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid sound: " + soundName + " — " + e.getMessage());
        }
    }

    private void handleWandPlace(PlayerInteractEvent event, Player player, ItemStack item, EquipmentSlot hand) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        if (!clickedBlock.getType().isSolid()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String wandModeString = meta.getPersistentDataContainer()
                .get(Constants.WAND_MODE, PersistentDataType.STRING);
        WandMode wandMode = WandMode.fromString(wandModeString);

        BlockFace clickedFace = event.getBlockFace();
        Material sourceMaterial = clickedBlock.getType();
        BlockData sourceData = clickedBlock.getBlockData();
        int maxBlocks = plugin.getConfigManager().getWandMaxBlocks();

        // Resolve offhand override material
        Material placeMaterial = sourceMaterial;
        BlockData placeData = sourceData;
        Material consumeMaterial = sourceMaterial;

        ItemStack overrideItem = InventoryUtil.resolveOverrideBlock(player, hand, plugin.getConfigManager());
        if (overrideItem != null) {
            placeMaterial = overrideItem.getType();
            placeData = placeMaterial.createBlockData();
            consumeMaterial = placeMaterial;
        }

        // BFS uses sourceMaterial to find connected surface; placements use placeMaterial
        List<Block> placements;
        if (wandMode == WandMode.FACE) {
            placements = WandPlacementCalculator.buildFacePlacement(
                    clickedBlock, clickedFace, sourceMaterial, maxBlocks, player, hand, item, plugin);
        } else {
            placements = WandPlacementCalculator.buildLinePlacement(
                    clickedBlock, clickedFace, sourceMaterial, maxBlocks, player, hand, item, plugin);
        }

        if (placements.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        // Check block consumption and limit to available blocks
        boolean consumeBlocks = plugin.getConfigManager().shouldConsumeBlocks(player.getGameMode());
        if (consumeBlocks) {
            int available = InventoryUtil.countMaterial(player, consumeMaterial);
            // Include offhand in count when using offhand override (countMaterial only covers slots 0-35)
            if (overrideItem != null) {
                available += overrideItem.getAmount();
            }
            if (available == 0) {
                event.setCancelled(true);
                return;
            }
            if (available < placements.size()) {
                placements = placements.subList(0, available);
            }
        }

        // Fire custom event with actual placed material
        WandPlaceEvent wandEvent = new WandPlaceEvent(
                player, placements, placeData, placeMaterial,
                item, hand, clickedFace, wandMode
        );
        plugin.getServer().getPluginManager().callEvent(wandEvent);

        if (wandEvent.isCancelled()) {
            plugin.debug("[Wand Debug] WandPlaceEvent was cancelled by another plugin");
            event.setCancelled(true);
            return;
        }

        // Capture snapshots for undo before placing
        List<WandHistoryManager.BlockSnapshot> snapshots = new ArrayList<>();
        for (Block block : placements) {
            snapshots.add(new WandHistoryManager.BlockSnapshot(block.getLocation(), block.getBlockData().clone()));
        }

        // Consume blocks from inventory (main inventory first, offhand last)
        if (consumeBlocks) {
            int remaining = consumeBlocks(player, consumeMaterial, placements.size());
            if (remaining > 0 && overrideItem != null) {
                consumeFromOffhand(player, hand, remaining);
            }
        }

        // Damage durability once (not per block)
        boolean broken = plugin.getDurabilityService().damage(item, player, 1);
        if (!broken) {
            plugin.getDisplayService().updateDisplay(item);
        }

        // Place all blocks
        for (Block block : placements) {
            block.setBlockData(placeData.clone(), true);
            plugin.getCoreProtectLogger().logWandPlacement(player, block, placeData);
        }

        // Record operation for undo
        historyManager.recordOperation(player, snapshots, placeData, placements.size(), consumeBlocks);

        // Play sound once at the first block position
        SoundUtil.playPlaceSound(placements.getFirst(), placeData, plugin);

        // Send placement message
        plugin.getMessageManager().send(player, "wand-placed",
            Placeholder.unparsed("count", String.valueOf(placements.size())));

        plugin.debug("[Wand Debug] Placed " + placements.size() + " blocks in " + wandMode + " mode");

        event.setCancelled(true);
    }

    /**
     * Consume blocks from main inventory (slots 0-35). Returns the number of blocks still needed.
     */
    private int consumeBlocks(Player player, Material material, int amount) {
        PlayerInventory inventory = player.getInventory();
        int remaining = amount;

        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot != null && slot.getType() == material) {
                if (slot.getAmount() > remaining) {
                    slot.setAmount(slot.getAmount() - remaining);
                    remaining = 0;
                } else {
                    remaining -= slot.getAmount();
                    inventory.setItem(i, null);
                }
            }
        }

        return remaining;
    }

    /**
     * Consume blocks from the offhand slot (last resort when main inventory is depleted).
     */
    private void consumeFromOffhand(Player player, EquipmentSlot wandHand, int amount) {
        EquipmentSlot otherHand = (wandHand == EquipmentSlot.HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack offhandItem = player.getInventory().getItem(otherHand);
        if (offhandItem == null) {
            return;
        }

        if (offhandItem.getAmount() <= amount) {
            player.getInventory().setItem(otherHand, null);
        } else {
            offhandItem.setAmount(offhandItem.getAmount() - amount);
        }
    }

}
