package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.events.TrowelPlaceEvent;
import dev.oakheart.oaktools.model.FeedSource;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.Constants;
import dev.oakheart.oaktools.util.InventoryUtil;
import dev.oakheart.oaktools.util.PlacementUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.FileConfiguration;
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
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;

/**
 * Handles Trowel tool interactions for random block placement and feed cycling.
 */
public class TrowelListener implements Listener {

    private final OakTools plugin;
    private final Random random;

    public TrowelListener(OakTools plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    /**
     * Check if debug logging is enabled in config.
     */
    private boolean isDebugEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("general.debug", false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTrowelFeedCycle(PlayerInteractEvent event) {
        // Handle feed source cycling at HIGHEST priority and don't ignore cancelled
        // This ensures it works even if other plugins cancel the event

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand();

        // Validate tool first
        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.TROWEL) {
            return;
        }

        // Only handle sneaking + right-click
        if (!player.isSneaking()) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Check permission
        if (!player.hasPermission("oaktools.use.trowel")) {
            return;
        }

        // Check if tool is enabled
        if (!plugin.getConfigManager().getConfig().getBoolean("tools.trowel.enabled", true)) {
            return;
        }

        // Cycle feed source
        cycleFeedSource(player, item, hand);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTrowelUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand();

        // Validate tool FIRST
        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return; // Not our tool, don't interfere at all
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.TROWEL) {
            return; // Not the Trowel tool
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

            // Check for interactive blocks with GUIs (stonecutters, crafting tables, etc.)
            if (isInteractiveBlock(clickedBlock)) {
                return; // Let vanilla handle opening the UI
            }
        }

        // Don't handle if sneaking (handled by feed cycle method)
        if (player.isSneaking()) {
            return;
        }

        // Check permission
        if (!player.hasPermission("oaktools.use.trowel")) {
            plugin.getMessageService().sendMessage(player, "no_permission");
            event.setCancelled(true);
            return;
        }

        // Check if tool is enabled
        if (!plugin.getConfigManager().getConfig().getBoolean("tools.trowel.enabled", true)) {
            return;
        }

        // Check gamemode
        if (!canUseInGamemode(player)) {
            return;
        }

        // Right-click block = place random block
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handlePlacement(event, player, item, hand);
        }
    }

    /**
     * Handle sneak + right-click on entity for feed cycling consistency.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTrowelEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack item = player.getInventory().getItem(hand);

        // Validate tool
        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        if (toolType != ToolType.TROWEL) {
            return;
        }

        // Sneak + right-click entity = cycle feed source
        if (player.isSneaking()) {
            cycleFeedSource(player, item, hand);
            event.setCancelled(true);
        }
    }

    /**
     * Cycle the feed source of the Trowel.
     */
    private void cycleFeedSource(Player player, ItemStack item, EquipmentSlot hand) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Get current feed source
        String feedSourceString = meta.getPersistentDataContainer()
                .get(Constants.FEED_SOURCE, PersistentDataType.STRING);
        FeedSource currentSource = FeedSource.fromString(feedSourceString);

        // Cycle to next source
        FeedSource nextSource = currentSource.next();

        // Update PDC
        meta.getPersistentDataContainer().set(Constants.FEED_SOURCE, PersistentDataType.STRING, nextSource.name());
        item.setItemMeta(meta);

        // Update display
        plugin.getDisplayService().updateDisplay(item);

        // Send feedback (pass null for %tool%, then feed source display name for %feed_source%)
        String feedSourceName = plugin.getDisplayService().getFeedSourceDisplayName(nextSource);
        plugin.getMessageService().sendMessage(player, "feed_source_changed",
            java.util.Map.of("feed_source", feedSourceName));

        // Play sound using Adventure API (modern, not deprecated)
        String soundName = plugin.getConfigManager().getConfig()
                .getString("tools.trowel.sounds.feed_source_switch", "ui.button.click");
        try {
            // Convert from format like "UI_BUTTON_CLICK" or "ui.button.click" to Adventure key
            String keyString = soundName.toLowerCase().replace('_', '.');
            if (!keyString.contains(":")) {
                keyString = "minecraft:" + keyString;
            }
            Key soundKey = Key.key(keyString);
            Sound sound = Sound.sound(soundKey, Sound.Source.PLAYER, 1.0f, 1.0f);
            player.playSound(sound);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid sound: " + soundName + " - " + e.getMessage());
        }
    }

    /**
     * Handle block placement with Trowel.
     */
    @SuppressWarnings("removal") // Bukkit Sound enum is deprecated but needed to get material's sound
    private void handlePlacement(PlayerInteractEvent event, Player player, ItemStack item, EquipmentSlot hand) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // Get feed source
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String feedSourceString = meta.getPersistentDataContainer()
                .get(Constants.FEED_SOURCE, PersistentDataType.STRING);
        FeedSource feedSource = FeedSource.fromString(feedSourceString);

        // Get placeable blocks from feed source
        List<ItemStack> placeableBlocks = InventoryUtil.getPlaceableBlocks(player, feedSource);

        if (placeableBlocks.isEmpty()) {
            String feedSourceName = plugin.getDisplayService().getFeedSourceDisplayName(feedSource);
            plugin.getMessageService().sendMessage(player, "no_placeable_blocks",
                java.util.Map.of("feed_source", feedSourceName));
            event.setCancelled(true);
            return;
        }

        // Choose random block
        ItemStack chosenBlock = placeableBlocks.get(random.nextInt(placeableBlocks.size()));

        // Determine target block location and placement reference
        // If clicked block is replaceable (grass, flowers, etc.), replace it directly
        // Otherwise, place in the adjacent block
        Block targetBlock;
        Block referenceBlock;  // The solid block to use for placement calculations
        BlockFace referenceFace = event.getBlockFace();

        if (isReplaceable(clickedBlock)) {
            // Placing into grass/flowers - replace them
            // For tall plants (tall_grass, sunflower, etc.), use the lower half
            if (clickedBlock.getBlockData() instanceof org.bukkit.block.data.Bisected bisected) {
                if (bisected.getHalf() == org.bukkit.block.data.Bisected.Half.TOP) {
                    // Clicked upper half - use lower half for all logic
                    clickedBlock = clickedBlock.getRelative(BlockFace.DOWN);
                    if (isDebugEnabled()) {
                        plugin.getLogger().info("[Trowel Debug] Clicked upper half of tall plant, using lower half");
                    }
                }
            }
            targetBlock = clickedBlock;

            // Perform a ray trace from the player to find what solid block they're looking at through the grass
            var rayTraceResult = player.rayTraceBlocks(6.0, org.bukkit.FluidCollisionMode.NEVER);

            if (isDebugEnabled()) {
                plugin.getLogger().info("[Trowel Debug] Placing through replaceable block at " +
                    clickedBlock.getX() + "," + clickedBlock.getY() + "," + clickedBlock.getZ() +
                    " (" + clickedBlock.getType() + ")");
                plugin.getLogger().info("[Trowel Debug] Event face: " + event.getBlockFace());
            }

            if (rayTraceResult != null && rayTraceResult.getHitBlock() != null) {
                Block hitBlock = rayTraceResult.getHitBlock();

                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Trowel Debug] Ray trace hit: " +
                        hitBlock.getX() + "," + hitBlock.getY() + "," + hitBlock.getZ() +
                        " (" + hitBlock.getType() + "), face: " + rayTraceResult.getHitBlockFace());
                }

                // Check if the hit block is adjacent to the grass and is solid
                if (!hitBlock.equals(clickedBlock) && !isReplaceable(hitBlock) && hitBlock.getType().isSolid()) {
                    // Check if this block is adjacent to the grass block
                    if (isAdjacent(clickedBlock, hitBlock)) {
                        referenceBlock = hitBlock;
                        referenceFace = rayTraceResult.getHitBlockFace();

                        if (isDebugEnabled()) {
                            plugin.getLogger().info("[Trowel Debug] Using ray trace result - refBlock: " +
                                referenceBlock.getX() + "," + referenceBlock.getY() + "," + referenceBlock.getZ() +
                                ", refFace: " + referenceFace);
                        }

                        // Use the actual hit position from rayTrace as our interaction point
                        // This will be used later for accurate slab/stair placement
                    } else {
                        // Hit block is not adjacent to grass - use grass itself
                        referenceBlock = clickedBlock;
                        referenceFace = event.getBlockFace();
                        if (isDebugEnabled()) {
                            plugin.getLogger().info("[Trowel Debug] Hit block not adjacent - using grass as reference");
                        }
                    }
                } else {
                    // Hit block is the grass itself or not solid
                    BlockFace eventFaceDirection = event.getBlockFace();
                    float pitch = player.getLocation().getPitch();
                    boolean lookingDown = pitch > 45.0f;

                    Block adjacentBlock = null;
                    BlockFace adjacentFace = null;

                    // Only check for adjacent blocks if NOT looking steeply down
                    if (!lookingDown) {
                        // Only check in the player's horizontal facing direction
                        BlockFace horizontalFacing = player.getFacing();
                        Block checkBlock = clickedBlock.getRelative(horizontalFacing);

                        // Only use if it's a full solid block (not slabs, stairs, etc.)
                        if (checkBlock.getType().isSolid() &&
                            !isReplaceable(checkBlock) &&
                            !(checkBlock.getBlockData() instanceof org.bukkit.block.data.type.Slab)) {
                            adjacentBlock = checkBlock;
                            adjacentFace = horizontalFacing.getOppositeFace();
                            if (isDebugEnabled()) {
                                plugin.getLogger().info("[Trowel Debug] Found adjacent full block in player facing direction " + horizontalFacing);
                            }
                        } else {
                            if (isDebugEnabled()) {
                                plugin.getLogger().info("[Trowel Debug] No adjacent full block in player facing direction " + horizontalFacing);
                            }
                        }
                    } else {
                        if (isDebugEnabled()) {
                            plugin.getLogger().info("[Trowel Debug] Looking down (pitch " +
                                String.format("%.1f", pitch) + "), skipping adjacent block check");
                        }
                    }

                    if (adjacentBlock != null) {
                        // Found adjacent block at same level as grass - use it
                        // This allows proper upper/lower half detection based on cursor
                        referenceBlock = adjacentBlock;
                        referenceFace = adjacentFace;
                        if (isDebugEnabled()) {
                            plugin.getLogger().info("[Trowel Debug] Using adjacent block at grass level with face " + adjacentFace);
                        }
                    } else {
                        // No adjacent block at grass level - default to bottom half by using UP face
                        Block blockBelow = clickedBlock.getRelative(BlockFace.DOWN);
                        if (blockBelow.getType().isSolid() && !isReplaceable(blockBelow)) {
                            referenceBlock = blockBelow;
                            referenceFace = BlockFace.UP;
                            if (isDebugEnabled()) {
                                plugin.getLogger().info("[Trowel Debug] No adjacent block, using block below → bottom half");
                            }
                        } else {
                            // Fallback to grass itself
                            referenceBlock = clickedBlock;
                            referenceFace = eventFaceDirection;
                            if (isDebugEnabled()) {
                                plugin.getLogger().info("[Trowel Debug] Using grass as reference");
                            }
                        }
                    }
                }
            } else {
                // No ray trace hit - use grass as reference
                referenceBlock = clickedBlock;
                referenceFace = event.getBlockFace();
                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Trowel Debug] No ray trace hit - using grass as reference");
                }
            }
        } else {
            targetBlock = clickedBlock.getRelative(event.getBlockFace());
            // Check if the adjacent block is replaceable
            if (!isReplaceable(targetBlock)) {
                return; // Silently fail if not replaceable
            }

            // Use the clicked block as reference (normal placement)
            referenceBlock = clickedBlock;
            referenceFace = event.getBlockFace();
        }

        // Apply vanilla placement logic
        BlockData placementData = chosenBlock.getType().createBlockData();

        // Get the interaction point (exact click location)
        Vector interactionPoint;

        // If we used ray tracing and found a reference block, use the ray trace hit position
        if (!referenceBlock.equals(clickedBlock)) {
            var rayTraceResult = player.rayTraceBlocks(6.0, org.bukkit.FluidCollisionMode.NEVER);
            if (rayTraceResult != null && rayTraceResult.getHitPosition() != null) {
                interactionPoint = rayTraceResult.getHitPosition();

                // If reference block is at or below clicked block (adjacent or diagonal case through grass)
                // Calculate where player's look vector intersects the reference block's face
                if (referenceBlock.getY() <= clickedBlock.getY()) {
                    Vector eyeLocation = player.getEyeLocation().toVector();
                    Vector direction = player.getLocation().getDirection();

                    // Find where ray intersects the reference block's opposite face
                    // For EAST event face, we want WEST face of reference block (X = refBlock.X)
                    double targetCoord;
                    int axis; // 0=X, 1=Y, 2=Z

                    if (referenceFace == BlockFace.WEST) {
                        // Player looking east, intersect with west face of ref block (X = refBlock.X)
                        targetCoord = referenceBlock.getX();
                        axis = 0;
                    } else if (referenceFace == BlockFace.EAST) {
                        // Player looking west, intersect with east face (X = refBlock.X + 1)
                        targetCoord = referenceBlock.getX() + 1.0;
                        axis = 0;
                    } else if (referenceFace == BlockFace.NORTH) {
                        // Player looking south, intersect with north face (Z = refBlock.Z)
                        targetCoord = referenceBlock.getZ();
                        axis = 2;
                    } else { // SOUTH
                        // Player looking north, intersect with south face (Z = refBlock.Z + 1)
                        targetCoord = referenceBlock.getZ() + 1.0;
                        axis = 2;
                    }

                    // Calculate intersection point
                    double t;
                    if (axis == 0) { // X axis
                        // Safety check: prevent division by zero when looking straight up/down
                        if (Math.abs(direction.getX()) < 0.001) {
                            if (isDebugEnabled()) {
                                plugin.getLogger().info("[Trowel Debug] Cannot calculate X-axis intersection (looking vertically), using fallback");
                            }
                            // Skip intersection calculation, use ray trace hit position as-is
                            // (will be handled by the existing interactionPoint assignment)
                        } else {
                            t = (targetCoord - eyeLocation.getX()) / direction.getX();
                            Vector intersection = eyeLocation.clone().add(direction.clone().multiply(t));

                            // Clamp Y to the reference block's bounds
                            double refBlockMinY = referenceBlock.getY();
                            double refBlockMaxY = referenceBlock.getY() + 1.0;
                            double clampedY = Math.max(refBlockMinY, Math.min(refBlockMaxY, intersection.getY()));
                            intersection.setY(clampedY);

                            interactionPoint = intersection;
                            if (isDebugEnabled()) {
                                plugin.getLogger().info("[Trowel Debug] Calculated diagonal block intersection at Y=" +
                                    String.format("%.2f", clampedY) + " (clamped to block bounds)");
                            }
                        }
                    } else { // Z axis
                        // Safety check: prevent division by zero when looking straight up/down
                        if (Math.abs(direction.getZ()) < 0.001) {
                            if (isDebugEnabled()) {
                                plugin.getLogger().info("[Trowel Debug] Cannot calculate Z-axis intersection (looking vertically), using fallback");
                            }
                            // Skip intersection calculation, use ray trace hit position as-is
                        } else {
                            t = (targetCoord - eyeLocation.getZ()) / direction.getZ();
                            Vector intersection = eyeLocation.clone().add(direction.clone().multiply(t));

                            // Clamp Y to the reference block's bounds
                            double refBlockMinY = referenceBlock.getY();
                            double refBlockMaxY = referenceBlock.getY() + 1.0;
                            double clampedY = Math.max(refBlockMinY, Math.min(refBlockMaxY, intersection.getY()));
                            intersection.setY(clampedY);

                            interactionPoint = intersection;
                            if (isDebugEnabled()) {
                                plugin.getLogger().info("[Trowel Debug] Calculated diagonal block intersection at Y=" +
                                    String.format("%.2f", clampedY) + " (clamped to block bounds)");
                            }
                        }
                    }
                } else {
                    // For non-diagonal cases, just use the ray trace hit position
                    // (no modification needed)
                }
                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Trowel Debug] Using ray trace hit position for interaction point");
                }
            } else if (event.getInteractionPoint() != null) {
                // Fallback to interaction point from event
                Vector clickPoint = event.getInteractionPoint().toVector();
                Vector clickedBlockPos = clickedBlock.getLocation().toVector();
                Vector relativePos = clickPoint.clone().subtract(clickedBlockPos);
                Vector referenceBlockPos = referenceBlock.getLocation().toVector();
                interactionPoint = referenceBlockPos.clone().add(relativePos);
                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Trowel Debug] Using event interaction point translated to reference block");
                }
            } else {
                interactionPoint = referenceBlock.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5));
                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Trowel Debug] Using reference block center");
                }
            }
        } else {
            // Using the clicked block as reference
            if (event.getInteractionPoint() != null) {
                interactionPoint = event.getInteractionPoint().toVector();
                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Trowel Debug] Using event interaction point directly");
                }
            } else {
                interactionPoint = referenceBlock.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5));
                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Trowel Debug] Using clicked block center");
                }
            }
        }

        if (isDebugEnabled()) {
            plugin.getLogger().info("[Trowel Debug] Final interaction point: " +
                String.format("%.2f,%.2f,%.2f", interactionPoint.getX(), interactionPoint.getY(), interactionPoint.getZ()) +
                ", Y mod 1: " + String.format("%.2f", interactionPoint.getY() % 1.0));
        }

        placementData = PlacementUtil.applyPlacementLogic(
                placementData,
                referenceFace,
                interactionPoint,
                player
        );

        if (isDebugEnabled()) {
            plugin.getLogger().info("[Trowel Debug] Placement data after logic: " + placementData.getAsString());
        }

        // Handle waterlogging if placing into water
        if (targetBlock.getType() == Material.WATER && placementData instanceof Waterlogged waterloggable) {
            waterloggable.setWaterlogged(true);
        }

        // Check if block would collide with player (vanilla behavior)
        // Prevent placing blocks where the player is standing
        if (wouldCollideWithPlayer(targetBlock, player)) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[Trowel Debug] Blocked: Block would collide with player");
            }
            event.setCancelled(true);
            return;
        }

        // Check protection BEFORE placing the block
        if (!plugin.getProtectionService().canModifyBlock(player, targetBlock, hand, item)) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[Trowel Debug] Blocked: Protection denied");
            }
            plugin.getMessageService().sendMessage(player, "protection_denied");
            event.setCancelled(true);
            return;
        }

        // Fire custom TrowelPlaceEvent
        TrowelPlaceEvent trowelEvent = new TrowelPlaceEvent(
                player, targetBlock, placementData, chosenBlock.getType(),
                item, chosenBlock, feedSource, hand, event.getBlockFace()
        );
        plugin.getServer().getPluginManager().callEvent(trowelEvent);

        if (trowelEvent.isCancelled()) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("[Trowel Debug] TrowelPlaceEvent was cancelled by another plugin");
            }
            event.setCancelled(true);
            return;
        }

        if (isDebugEnabled()) {
            plugin.getLogger().info("[Trowel Debug] TrowelPlaceEvent was not cancelled, proceeding");
        }

        // TRANSACTION SAFETY: Consume item BEFORE placing block
        // This prevents item duplication if server crashes after placement
        if (shouldConsumeBlocks(player)) {
            if (!InventoryUtil.consumeItem(player, chosenBlock)) {
                // Item couldn't be consumed (shouldn't happen, but safety check)
                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Trowel Debug] Failed to consume item, cancelling");
                }
                event.setCancelled(true);
                return;
            }
        }

        // Damage tool durability (may break tool, but action still completes - vanilla behavior)
        plugin.getDurabilityService().damage(item, player, 1);

        // Update display (PDC data reflects new durability)
        plugin.getDisplayService().updateDisplay(item);

        // Place the block (even if tool broke, the action completes)
        targetBlock.setBlockData(placementData, true);

        if (isDebugEnabled()) {
            plugin.getLogger().info("[Trowel Debug] Block placed successfully at " +
                targetBlock.getX() + "," + targetBlock.getY() + "," + targetBlock.getZ());
        }

        // Play the block's place sound
        // Use string-based sound key instead of enum to avoid Paper 1.21.8 enum mapping issues
        if (isDebugEnabled()) {
            plugin.getLogger().info("[Trowel Debug] Attempting to play sound for block placement");
            plugin.getLogger().info("[Trowel Debug] Material being placed: " + placementData.getMaterial());
        }
        try {
            // Use the PLACED block's sound group
            org.bukkit.SoundGroup soundGroup = targetBlock.getBlockData().getSoundGroup();
            org.bukkit.Sound bukkitSound = soundGroup.getPlaceSound();
            float volume = soundGroup.getVolume();
            float pitch = soundGroup.getPitch();

            if (isDebugEnabled()) {
                plugin.getLogger().info("[Trowel Debug] SoundGroup: " + soundGroup);
                plugin.getLogger().info("[Trowel Debug] Place sound enum: " + (bukkitSound != null ? bukkitSound.name() : "NULL"));
                plugin.getLogger().info("[Trowel Debug] Volume from SoundGroup: " + volume);
                plugin.getLogger().info("[Trowel Debug] Pitch from SoundGroup: " + pitch);
            }

            if (bukkitSound != null) {
                // Use sound group's volume and pitch (vanilla behavior)
                // Add slight randomization to pitch like vanilla (0.8 * pitch to 1.2 * pitch)
                float randomPitch = pitch * (0.8f + (float)(Math.random() * 0.4));

                // Use Bukkit's getKey() to get the proper namespaced key (preserves underscores correctly)
                // String-based approach works for all sounds (Adventure API has bugs with stone/wood sounds)
                String soundKey = bukkitSound.getKey().asString();

                org.bukkit.Location soundLoc = targetBlock.getLocation().add(0.5, 0.5, 0.5);

                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Trowel Debug] Sound key: " + soundKey);
                }

                // Use string-based sound key (works for all sounds including stone/wood)
                player.playSound(soundLoc, soundKey, org.bukkit.SoundCategory.BLOCKS, volume, randomPitch);

                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Trowel Debug] Played sound: " + soundKey +
                        ", volume: " + volume + ", pitch: " + randomPitch);
                }
            } else {
                if (isDebugEnabled()) {
                    plugin.getLogger().info("[Trowel Debug] No place sound for " + placementData.getMaterial());
                }
            }
        } catch (Exception e) {
            if (isDebugEnabled()) {
                plugin.getLogger().warning("[Trowel Debug] Error playing sound: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Log to CoreProtect
        plugin.getCoreProtectLogger().logTrowelPlacement(player, targetBlock, placementData);

        event.setCancelled(true);
    }

    /**
     * Check if a block is replaceable (can be placed into).
     */
    private boolean isReplaceable(Block block) {
        Material type = block.getType();

        // Check if block is air, water, or lava (always replaceable)
        if (type.isAir() || type == Material.WATER || type == Material.LAVA) {
            return true;
        }

        // Check config list
        FileConfiguration config = plugin.getConfigManager().getConfig();
        List<String> replaceableList = config.getStringList("tools.trowel.can_replace");

        for (String materialName : replaceableList) {
            try {
                Material replaceable = Material.valueOf(materialName);
                if (type == replaceable) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Invalid material in config, skip
            }
        }

        // Only blocks in the config list are replaceable
        return false;
    }

    /**
     * Check if player can use Trowel in their current gamemode.
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
     * Check if blocks should be consumed from inventory.
     */
    private boolean shouldConsumeBlocks(Player player) {
        GameMode mode = player.getGameMode();
        FileConfiguration config = plugin.getConfigManager().getConfig();

        return switch (mode) {
            case CREATIVE -> config.getBoolean("general.restrictions.gamemode.creative.consume_blocks", false);
            case ADVENTURE -> true;
            default -> true;
        };
    }

    /**
     * Check if two blocks are adjacent (share a face).
     */
    private boolean isAdjacent(Block block1, Block block2) {
        int dx = Math.abs(block1.getX() - block2.getX());
        int dy = Math.abs(block1.getY() - block2.getY());
        int dz = Math.abs(block1.getZ() - block2.getZ());

        // Adjacent blocks differ by exactly 1 in one dimension and 0 in the others
        return (dx == 1 && dy == 0 && dz == 0) ||
               (dx == 0 && dy == 1 && dz == 0) ||
               (dx == 0 && dy == 0 && dz == 1);
    }

    /**
     * Check if placing a block would collide with any player's hitbox.
     * Mimics vanilla Minecraft behavior - you cannot place blocks where any player is standing.
     */
    private boolean wouldCollideWithPlayer(Block targetBlock, Player placingPlayer) {
        // Target block bounding box (1x1x1 cube)
        int blockX = targetBlock.getX();
        int blockY = targetBlock.getY();
        int blockZ = targetBlock.getZ();
        double blockMinX = blockX;
        double blockMaxX = blockX + 1.0;
        double blockMinY = blockY;
        double blockMaxY = blockY + 1.0;
        double blockMinZ = blockZ;
        double blockMaxZ = blockZ + 1.0;

        // Check collision with all nearby players (within 5 blocks for performance)
        for (org.bukkit.entity.Entity entity : targetBlock.getWorld().getNearbyEntities(
                targetBlock.getLocation().add(0.5, 0.5, 0.5), 5, 5, 5)) {

            if (!(entity instanceof Player)) {
                continue;
            }

            Player nearbyPlayer = (Player) entity;

            // Get player's bounding box
            org.bukkit.Location playerLoc = nearbyPlayer.getLocation();
            double playerX = playerLoc.getX();
            double playerY = playerLoc.getY();
            double playerZ = playerLoc.getZ();

            // Player hitbox: 0.6 blocks wide (x/z), 1.8 blocks tall (y)
            // Centered on player location
            double halfWidth = 0.3; // 0.6 / 2
            double playerMinX = playerX - halfWidth;
            double playerMaxX = playerX + halfWidth;
            double playerMinY = playerY;
            double playerMaxY = playerY + 1.8;
            double playerMinZ = playerZ - halfWidth;
            double playerMaxZ = playerZ + halfWidth;

            // AABB collision detection
            boolean collides = playerMaxX > blockMinX && playerMinX < blockMaxX &&
                              playerMaxY > blockMinY && playerMinY < blockMaxY &&
                              playerMaxZ > blockMinZ && playerMinZ < blockMaxZ;

            if (collides) {
                return true; // Block would collide with this player
            }
        }

        return false; // No collision with any player
    }

    /**
     * Check if a block is an interactive block with a GUI that should open.
     * These blocks should not be replaced/placed into when using the Trowel.
     *
     * @param block the block to check
     * @return true if the block should open a GUI instead
     */
    private boolean isInteractiveBlock(Block block) {
        return switch (block.getType()) {
            case CRAFTING_TABLE,
                 STONECUTTER,
                 LOOM,
                 GRINDSTONE,
                 CARTOGRAPHY_TABLE,
                 SMITHING_TABLE,
                 ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 ENCHANTING_TABLE,
                 ENDER_CHEST,
                 // Beds (right-click to sleep)
                 WHITE_BED, ORANGE_BED, MAGENTA_BED, LIGHT_BLUE_BED, YELLOW_BED,
                 LIME_BED, PINK_BED, GRAY_BED, LIGHT_GRAY_BED, CYAN_BED,
                 PURPLE_BED, BLUE_BED, BROWN_BED, GREEN_BED, RED_BED, BLACK_BED,
                 // Redstone components
                 LEVER,
                 REPEATER,
                 COMPARATOR,
                 // Buttons (all types)
                 OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON, JUNGLE_BUTTON,
                 ACACIA_BUTTON, DARK_OAK_BUTTON, MANGROVE_BUTTON, CHERRY_BUTTON,
                 BAMBOO_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON,
                 STONE_BUTTON, POLISHED_BLACKSTONE_BUTTON,
                 // Note blocks
                 NOTE_BLOCK,
                 // Dragon egg
                 DRAGON_EGG,
                 // Respawn anchor
                 RESPAWN_ANCHOR,
                 // Bell
                 BELL,
                 // Cake
                 CAKE -> true;
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
