package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.events.TrowelPlaceEvent;
import dev.oakheart.oaktools.model.FeedSource;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.BlockUtil;
import dev.oakheart.oaktools.util.Constants;
import dev.oakheart.oaktools.util.InventoryUtil;
import dev.oakheart.oaktools.util.PlacementUtil;
import dev.oakheart.oaktools.util.SoundUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TrowelListener implements Listener {

    private final OakTools plugin;

    public TrowelListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onTrowelFeedCycle(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.TROWEL) {
            return;
        }

        if (!player.isSneaking()) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!player.hasPermission("oaktools.use.trowel")) {
            return;
        }

        if (!plugin.getConfigManager().isTrowelEnabled()) {
            return;
        }

        if (!plugin.getConfigManager().isWorldAllowed(player.getWorld().getName())) {
            plugin.getMessageManager().send(player, "world-denied");
            event.setCancelled(true);
            return;
        }

        cycleFeedSource(player, item, event.getHand());
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTrowelUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand();

        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.TROWEL) {
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

            if (BlockUtil.isInteractiveBlock(clickedBlock)) {
                return;
            }
        }

        if (player.isSneaking()) {
            return;
        }

        if (!player.hasPermission("oaktools.use.trowel")) {
            plugin.getMessageManager().send(player, "no-permission");
            event.setCancelled(true);
            return;
        }

        if (!plugin.getConfigManager().isTrowelEnabled()) {
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
            handlePlacement(event, player, item, hand);
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTrowelEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack item = player.getInventory().getItem(hand);

        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.TROWEL) {
            return;
        }

        if (player.isSneaking()) {
            cycleFeedSource(player, item, hand);
            event.setCancelled(true);
        }
    }

    private void cycleFeedSource(Player player, ItemStack item, EquipmentSlot hand) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String feedSourceString = meta.getPersistentDataContainer()
                .get(Constants.FEED_SOURCE, PersistentDataType.STRING);
        FeedSource currentSource = FeedSource.fromString(feedSourceString);

        FeedSource nextSource = currentSource.next();

        meta.getPersistentDataContainer().set(Constants.FEED_SOURCE, PersistentDataType.STRING, nextSource.name());
        item.setItemMeta(meta);

        plugin.getDisplayService().updateDisplay(item);

        String feedSourceName = plugin.getDisplayService().getFeedSourceDisplayName(nextSource);
        plugin.getMessageManager().send(player, "feed-source-changed",
            Placeholder.unparsed("feed_source", feedSourceName));

        String soundName = plugin.getConfigManager().getTrowelFeedSwitchSound();
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

    private void handlePlacement(PlayerInteractEvent event, Player player, ItemStack item, EquipmentSlot hand) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String feedSourceString = meta.getPersistentDataContainer()
                .get(Constants.FEED_SOURCE, PersistentDataType.STRING);
        FeedSource feedSource = FeedSource.fromString(feedSourceString);

        List<ItemStack> placeableBlocks = InventoryUtil.getPlaceableBlocks(player, feedSource);

        if (placeableBlocks.isEmpty()) {
            String feedSourceName = plugin.getDisplayService().getFeedSourceDisplayName(feedSource);
            plugin.getMessageManager().send(player, "no-placeable-blocks",
                Placeholder.unparsed("feed_source", feedSourceName));
            event.setCancelled(true);
            return;
        }

        ItemStack chosenBlock = placeableBlocks.get(ThreadLocalRandom.current().nextInt(placeableBlocks.size()));

        // Resolve target block and reference block for placement
        TargetResolution target = resolveTargetBlock(clickedBlock, event, player);
        if (target == null) {
            return;
        }

        Block targetBlock = target.targetBlock;
        Block referenceBlock = target.referenceBlock;
        BlockFace referenceFace = target.referenceFace;

        // Apply vanilla placement logic
        BlockData placementData = chosenBlock.getType().createBlockData();

        // Calculate interaction point
        Vector interactionPoint = calculateInteractionPoint(
                clickedBlock, referenceBlock, target.cachedRayTrace, event, player);

        plugin.debug("[Trowel Debug] Final interaction point: " +
            String.format("%.2f,%.2f,%.2f", interactionPoint.getX(), interactionPoint.getY(), interactionPoint.getZ()) +
            ", Y mod 1: " + String.format("%.2f", interactionPoint.getY() % 1.0));

        placementData = PlacementUtil.applyPlacementLogic(
                placementData,
                referenceFace,
                interactionPoint,
                player
        );

        plugin.debug("[Trowel Debug] Placement data after logic: " + placementData.getAsString());

        if (targetBlock.getType() == Material.WATER && placementData instanceof Waterlogged waterloggable) {
            waterloggable.setWaterlogged(true);
        }

        if (wouldCollideWithPlayer(targetBlock, player)) {
            plugin.debug("[Trowel Debug] Blocked: Block would collide with player");
            event.setCancelled(true);
            return;
        }

        if (!plugin.getProtectionService().canModifyBlock(player, targetBlock, hand, item)) {
            plugin.debug("[Trowel Debug] Blocked: Protection denied");
            plugin.getMessageManager().send(player, "protection-denied");
            event.setCancelled(true);
            return;
        }

        TrowelPlaceEvent trowelEvent = new TrowelPlaceEvent(
                player, targetBlock, placementData, chosenBlock.getType(),
                item, chosenBlock, feedSource, hand, event.getBlockFace()
        );
        plugin.getServer().getPluginManager().callEvent(trowelEvent);

        if (trowelEvent.isCancelled()) {
            plugin.debug("[Trowel Debug] TrowelPlaceEvent was cancelled by another plugin");
            event.setCancelled(true);
            return;
        }

        plugin.debug("[Trowel Debug] TrowelPlaceEvent was not cancelled, proceeding");

        if (plugin.getConfigManager().shouldConsumeBlocks(player.getGameMode())) {
            if (!InventoryUtil.consumeItem(player, chosenBlock)) {
                plugin.debug("[Trowel Debug] Failed to consume item, cancelling");
                event.setCancelled(true);
                return;
            }
        }

        boolean broken = plugin.getDurabilityService().damage(item, player, 1);
        if (!broken) {
            plugin.getDisplayService().updateDisplay(item);
        }

        targetBlock.setBlockData(placementData, true);

        plugin.debug("[Trowel Debug] Block placed successfully at " +
            targetBlock.getX() + "," + targetBlock.getY() + "," + targetBlock.getZ());

        SoundUtil.playPlaceSound(targetBlock, placementData, plugin);

        plugin.getCoreProtectLogger().logTrowelPlacement(player, targetBlock, placementData);

        event.setCancelled(true);
    }

    /**
     * Helper record for target resolution results.
     */
    private record TargetResolution(Block targetBlock, Block referenceBlock, BlockFace referenceFace,
                                     RayTraceResult cachedRayTrace) {}

    /**
     * Resolve the target block for placement, handling replaceable blocks and ray tracing.
     */
    private TargetResolution resolveTargetBlock(Block clickedBlock, PlayerInteractEvent event, Player player) {
        Block targetBlock;
        Block referenceBlock;
        BlockFace referenceFace = event.getBlockFace();
        RayTraceResult cachedRayTrace = null;

        if (isReplaceable(clickedBlock)) {
            // For tall plants, use the lower half
            if (clickedBlock.getBlockData() instanceof org.bukkit.block.data.Bisected bisected) {
                if (bisected.getHalf() == org.bukkit.block.data.Bisected.Half.TOP) {
                    clickedBlock = clickedBlock.getRelative(BlockFace.DOWN);
                    plugin.debug("[Trowel Debug] Clicked upper half of tall plant, using lower half");
                }
            }
            targetBlock = clickedBlock;

            cachedRayTrace = player.rayTraceBlocks(6.0, org.bukkit.FluidCollisionMode.NEVER);

            plugin.debug("[Trowel Debug] Placing through replaceable block at " +
                clickedBlock.getX() + "," + clickedBlock.getY() + "," + clickedBlock.getZ() +
                " (" + clickedBlock.getType() + ")");

            if (cachedRayTrace != null && cachedRayTrace.getHitBlock() != null) {
                Block hitBlock = cachedRayTrace.getHitBlock();

                plugin.debug("[Trowel Debug] Ray trace hit: " +
                    hitBlock.getX() + "," + hitBlock.getY() + "," + hitBlock.getZ() +
                    " (" + hitBlock.getType() + "), face: " + cachedRayTrace.getHitBlockFace());

                if (!hitBlock.equals(clickedBlock) && !isReplaceable(hitBlock) && hitBlock.getType().isSolid()) {
                    if (isAdjacent(clickedBlock, hitBlock)) {
                        referenceBlock = hitBlock;
                        referenceFace = cachedRayTrace.getHitBlockFace();
                        plugin.debug("[Trowel Debug] Using ray trace result — refBlock: " +
                            referenceBlock.getX() + "," + referenceBlock.getY() + "," + referenceBlock.getZ() +
                            ", refFace: " + referenceFace);
                    } else {
                        referenceBlock = clickedBlock;
                        referenceFace = event.getBlockFace();
                        plugin.debug("[Trowel Debug] Hit block not adjacent — using grass as reference");
                    }
                } else {
                    BlockFace eventFaceDirection = event.getBlockFace();
                    float pitch = player.getLocation().getPitch();
                    boolean lookingDown = pitch > 45.0f;

                    Block adjacentBlock = null;
                    BlockFace adjacentFace = null;

                    if (!lookingDown) {
                        BlockFace horizontalFacing = player.getFacing();
                        Block checkBlock = clickedBlock.getRelative(horizontalFacing);

                        if (checkBlock.getType().isSolid() &&
                            !isReplaceable(checkBlock) &&
                            !(checkBlock.getBlockData() instanceof org.bukkit.block.data.type.Slab)) {
                            adjacentBlock = checkBlock;
                            adjacentFace = horizontalFacing.getOppositeFace();
                            plugin.debug("[Trowel Debug] Found adjacent full block in player facing direction " + horizontalFacing);
                        }
                    }

                    if (adjacentBlock != null) {
                        referenceBlock = adjacentBlock;
                        referenceFace = adjacentFace;
                        plugin.debug("[Trowel Debug] Using adjacent block at grass level with face " + adjacentFace);
                    } else {
                        Block blockBelow = clickedBlock.getRelative(BlockFace.DOWN);
                        if (blockBelow.getType().isSolid() && !isReplaceable(blockBelow)) {
                            referenceBlock = blockBelow;
                            referenceFace = BlockFace.UP;
                            plugin.debug("[Trowel Debug] No adjacent block, using block below → bottom half");
                        } else {
                            referenceBlock = clickedBlock;
                            referenceFace = eventFaceDirection;
                            plugin.debug("[Trowel Debug] Using grass as reference");
                        }
                    }
                }
            } else {
                referenceBlock = clickedBlock;
                referenceFace = event.getBlockFace();
                plugin.debug("[Trowel Debug] No ray trace hit — using grass as reference");
            }
        } else {
            targetBlock = clickedBlock.getRelative(event.getBlockFace());
            if (!isReplaceable(targetBlock)) {
                return null;
            }

            referenceBlock = clickedBlock;
            referenceFace = event.getBlockFace();
        }

        return new TargetResolution(targetBlock, referenceBlock, referenceFace, cachedRayTrace);
    }

    /**
     * Calculate the interaction point for placement logic.
     */
    private Vector calculateInteractionPoint(Block clickedBlock, Block referenceBlock,
                                              RayTraceResult cachedRayTrace,
                                              PlayerInteractEvent event, Player player) {
        if (!referenceBlock.equals(clickedBlock)) {
            if (cachedRayTrace != null && cachedRayTrace.getHitPosition() != null) {
                return calculateRayPlaneIntersection(player, referenceBlock, clickedBlock, cachedRayTrace, event);
            } else if (event.getInteractionPoint() != null) {
                return transferInteractionPoint(clickedBlock, referenceBlock, event);
            } else {
                return referenceBlock.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5));
            }
        } else {
            if (event.getInteractionPoint() != null) {
                return event.getInteractionPoint().toVector();
            } else {
                return referenceBlock.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5));
            }
        }
    }

    /**
     * Calculate where the player's eye ray intersects the reference block's face plane.
     * Used when placing through a replaceable block to get accurate slab half / stair orientation.
     */
    private Vector calculateRayPlaneIntersection(Player player, Block referenceBlock, Block clickedBlock,
                                                  RayTraceResult cachedRayTrace, PlayerInteractEvent event) {
        Vector interactionPoint = cachedRayTrace.getHitPosition();

        if (referenceBlock.getY() > clickedBlock.getY()) {
            return interactionPoint;
        }

        Vector eyeLocation = player.getEyeLocation().toVector();
        Vector direction = player.getLocation().getDirection();

        BlockFace referenceFace = cachedRayTrace.getHitBlockFace();
        if (referenceFace == null) {
            referenceFace = event.getBlockFace();
        }

        double targetCoord;
        boolean isXAxis;

        if (referenceFace == BlockFace.WEST) {
            targetCoord = referenceBlock.getX();
            isXAxis = true;
        } else if (referenceFace == BlockFace.EAST) {
            targetCoord = referenceBlock.getX() + 1.0;
            isXAxis = true;
        } else if (referenceFace == BlockFace.NORTH) {
            targetCoord = referenceBlock.getZ();
            isXAxis = false;
        } else {
            targetCoord = referenceBlock.getZ() + 1.0;
            isXAxis = false;
        }

        double dirComponent = isXAxis ? direction.getX() : direction.getZ();
        double eyeComponent = isXAxis ? eyeLocation.getX() : eyeLocation.getZ();

        if (Math.abs(dirComponent) >= 0.001) {
            double t = (targetCoord - eyeComponent) / dirComponent;
            Vector intersection = eyeLocation.clone().add(direction.clone().multiply(t));
            double clampedY = Math.max(referenceBlock.getY(), Math.min(referenceBlock.getY() + 1.0, intersection.getY()));
            intersection.setY(clampedY);
            return intersection;
        }

        return interactionPoint;
    }

    /**
     * Transfer the click's relative position from the clicked block to the reference block.
     * Used as fallback when ray trace data is unavailable.
     */
    private Vector transferInteractionPoint(Block clickedBlock, Block referenceBlock, PlayerInteractEvent event) {
        Vector clickPoint = event.getInteractionPoint().toVector();
        Vector clickedBlockPos = clickedBlock.getLocation().toVector();
        Vector relativePos = clickPoint.clone().subtract(clickedBlockPos);
        Vector referenceBlockPos = referenceBlock.getLocation().toVector();
        return referenceBlockPos.clone().add(relativePos);
    }

    private boolean isReplaceable(Block block) {
        Material type = block.getType();

        if (type.isAir() || type == Material.WATER || type == Material.LAVA) {
            return true;
        }

        return plugin.getConfigManager().getReplaceableMaterials().contains(type);
    }

    private boolean isAdjacent(Block block1, Block block2) {
        int dx = Math.abs(block1.getX() - block2.getX());
        int dy = Math.abs(block1.getY() - block2.getY());
        int dz = Math.abs(block1.getZ() - block2.getZ());

        return (dx == 1 && dy == 0 && dz == 0) ||
               (dx == 0 && dy == 1 && dz == 0) ||
               (dx == 0 && dy == 0 && dz == 1);
    }

    private boolean wouldCollideWithPlayer(Block targetBlock, Player placingPlayer) {
        double blockMinX = targetBlock.getX();
        double blockMaxX = targetBlock.getX() + 1.0;
        double blockMinY = targetBlock.getY();
        double blockMaxY = targetBlock.getY() + 1.0;
        double blockMinZ = targetBlock.getZ();
        double blockMaxZ = targetBlock.getZ() + 1.0;

        for (org.bukkit.entity.Entity entity : targetBlock.getWorld().getNearbyEntities(
                targetBlock.getLocation().add(0.5, 0.5, 0.5), 2, 2, 2)) {

            if (!(entity instanceof Player nearbyPlayer)) {
                continue;
            }

            org.bukkit.util.BoundingBox playerBox = nearbyPlayer.getBoundingBox();

            boolean collides = playerBox.getMaxX() > blockMinX && playerBox.getMinX() < blockMaxX &&
                              playerBox.getMaxY() > blockMinY && playerBox.getMinY() < blockMaxY &&
                              playerBox.getMaxZ() > blockMinZ && playerBox.getMinZ() < blockMaxZ;

            if (collides) {
                return true;
            }
        }

        return false;
    }
}
