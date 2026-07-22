package dev.oakheart.oaktools.managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.events.OakToolBlockBreakEvent;
import dev.oakheart.oaktools.integration.OverflowHook;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.Constants;
import dev.oakheart.oaktools.util.DropHandler;
import com.github.retrooper.packetevents.util.Vector3i;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central animated block breaking system for harvesting tools.
 * Processes one block per configured tick interval per active operation.
 */
public class BreakingAnimationManager implements Listener {

    private final OakTools plugin;
    private final OverflowHook overflowHook;
    private final Map<UUID, BreakingOperation> activeOperations = new HashMap<>();
    private final Set<Location> frozenBlocks = new HashSet<>();
    private BukkitTask tickTask;
    private boolean usePackets;

    // Fake entity IDs for block break animation packets
    private static final AtomicInteger ANIMATION_ENTITY_ID = new AtomicInteger(2_000_000_000);

    public BreakingAnimationManager(OakTools plugin, OverflowHook overflowHook) {
        this.plugin = plugin;
        this.overflowHook = overflowHook;
    }

    public void start() {
        usePackets = Bukkit.getPluginManager().getPlugin("packetevents") != null;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        activeOperations.clear();
        frozenBlocks.clear();
    }

    public boolean hasActiveOperation(UUID playerId) {
        return activeOperations.containsKey(playerId);
    }

    /**
     * Starts a new breaking operation for a player.
     * If the player already has an active operation, it is cancelled first.
     */
    public void startOperation(Player player, ToolType toolType, List<Block> blocks,
                               String progressMessageKey, String completeMessageKey) {
        startOperation(player, toolType, blocks, progressMessageKey, completeMessageKey, 0, List.of());
    }

    /**
     * @param alreadyBroken number of blocks already broken before this operation (e.g. the initial block)
     * @param initialOverflow overflow items from blocks already broken
     */
    public void startOperation(Player player, ToolType toolType, List<Block> blocks,
                               String progressMessageKey, String completeMessageKey,
                               int alreadyBroken, List<ItemStack> initialOverflow) {
        UUID uuid = player.getUniqueId();

        // Cancel existing operation if any
        BreakingOperation existing = activeOperations.remove(uuid);
        if (existing != null) {
            clearCrackAnimation(existing);
            unfreezeBlocks(existing);
        }

        int tickInterval = plugin.getConfigManager().getBreakSpeedTicks(toolType);
        boolean showProgress = plugin.getConfigManager().isShowProgress(toolType);
        BreakingOperation operation = new BreakingOperation(
                uuid, toolType, blocks, tickInterval,
                progressMessageKey, completeMessageKey, blocks.size() + alreadyBroken, showProgress);
        operation.brokenCount = alreadyBroken;
        operation.accumulatedOverflow.addAll(initialOverflow);
        freezeBlocks(operation);
        activeOperations.put(uuid, operation);
    }

    public void cancelOperation(UUID playerId) {
        BreakingOperation operation = activeOperations.remove(playerId);
        if (operation != null) {
            clearCrackAnimation(operation);
            unfreezeBlocks(operation);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                flushOverflow(player, operation);
                plugin.getMessageManager().send(player, "operation-cancelled");
            }
        }
    }

    private void tick() {
        var iterator = activeOperations.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID playerId = entry.getKey();
            BreakingOperation op = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);

            // Backstop only — death/quit events flush overflow while the player
            // is still usable; anything reaching this branch has no player to
            // flush to.
            if (player == null || player.isDead()) {
                clearCrackAnimation(op);
                unfreezeBlocks(op);
                iterator.remove();
                continue;
            }

            // Cancel if player switched away from the tool
            if (!isHoldingToolType(player, op.toolType)) {
                clearCrackAnimation(op);
                unfreezeBlocks(op);
                flushOverflow(player, op);
                plugin.getMessageManager().send(player, "operation-cancelled");
                iterator.remove();
                continue;
            }

            // Cancel if player started sneaking
            if (player.isSneaking()) {
                clearCrackAnimation(op);
                unfreezeBlocks(op);
                flushOverflow(player, op);
                plugin.getMessageManager().send(player, "operation-cancelled");
                iterator.remove();
                continue;
            }

            // Check tick interval
            op.tickCounter++;
            if (op.tickCounter < op.tickInterval) {
                continue;
            }
            op.tickCounter = 0;

            // Find next valid block
            Block block = advanceToNextBlock(op);
            if (block == null) {
                // All blocks processed — operation complete
                clearCrackAnimation(op);
                unfreezeBlocks(op);
                flushOverflow(player, op);
                if (op.showProgress) {
                    plugin.getMessageManager().send(player, op.completeMessageKey,
                            Placeholder.unparsed("count", String.valueOf(op.brokenCount)));
                }
                iterator.remove();
                continue;
            }

            // Protection check
            if (!plugin.getProtectionService().canBreakBlock(player, block)) {
                continue; // Skip this block, try next on next tick
            }

            BlockData blockData = block.getBlockData();

            // CoreProtect log BEFORE breaking
            plugin.getCoreProtectLogger().logHarvestingBreak(player, block, blockData);

            // Unfreeze this block before breaking (allow physics to resume for it)
            frozenBlocks.remove(block.getLocation());

            // Break block and distribute drops to inventory. The main-hand item
            // is the operation's tool — isHoldingToolType verified it above.
            DropHandler.Result result = DropHandler.handleBlockBreak(player, block, op.toolType,
                    player.getInventory().getItemInMainHand());
            op.accumulatedOverflow.addAll(result.overflowItems());

            // Play break particles and sound
            playBreakEffects(block, blockData);

            op.brokenCount++;

            // Announce the cascade break: it fired no BlockBreakEvent (we cleared
            // it directly above), so without this a whole tree/vein/dig counts as
            // one block to any listener - quest objectives especially.
            Bukkit.getPluginManager().callEvent(
                    new OakToolBlockBreakEvent(player, block, blockData.getMaterial(), op.toolType));

            // Damage tool (if not unbreakable)
            if (!plugin.getConfigManager().isUnbreakable(op.toolType)) {
                ItemStack heldItem = player.getInventory().getItemInMainHand();
                if (plugin.getItemFactory().isTool(heldItem)) {
                    boolean broken = plugin.getDurabilityService().damage(heldItem, player, 1);
                    if (broken) {
                        // Tool broke — cancel remaining blocks
                        clearCrackAnimation(op);
                        unfreezeBlocks(op);
                        flushOverflow(player, op);
                        iterator.remove();
                        continue;
                    }
                    plugin.getDisplayService().updateDisplay(heldItem);
                }
            }

            // Send progress message
            if (op.showProgress) {
                plugin.getMessageManager().send(player, op.progressMessageKey,
                        Placeholder.unparsed("count", String.valueOf(op.brokenCount)),
                        Placeholder.unparsed("total", String.valueOf(op.totalBlocks)));
            }

            // Show crack animation on next block in queue
            sendCrackAnimation(op, player);
        }
    }

    /**
     * Advances the operation to the next valid (non-air, correct type) block.
     * Returns null if no more blocks remain.
     */
    private Block advanceToNextBlock(BreakingOperation op) {
        while (op.currentIndex < op.blocks.size()) {
            Block block = op.blocks.get(op.currentIndex);
            op.currentIndex++;
            if (block.getType() != Material.AIR) {
                return block;
            }
        }
        return null;
    }

    private void playBreakEffects(Block block, BlockData blockData) {
        block.getWorld().spawnParticle(Particle.BLOCK,
                block.getLocation().add(0.5, 0.5, 0.5),
                30, 0.3, 0.3, 0.3, blockData);

        block.getWorld().playSound(block.getLocation(),
                blockData.getSoundGroup().getBreakSound(), 1.0f, 1.0f);
    }

    private boolean isHoldingToolType(Player player, ToolType expectedType) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        String typeStr = meta.getPersistentDataContainer().get(Constants.TOOL_TYPE, PersistentDataType.STRING);
        if (typeStr == null) return false;

        ToolType held = ToolType.fromString(typeStr);
        return held == expectedType;
    }

    // ===== Block crack animation via PacketEvents =====

    private void sendCrackAnimation(BreakingOperation op, Player player) {
        if (!usePackets) return;

        // Clear previous crack before showing a new one (prevents lingering cracks
        // on blocks that were already broken — visible when sand falls into those positions)
        clearCrackAnimation(op);

        // Find next block to show crack on
        int peekIndex = op.currentIndex;
        while (peekIndex < op.blocks.size()) {
            Block next = op.blocks.get(peekIndex);
            if (next.getType() != Material.AIR) {
                op.crackEntityId = ANIMATION_ENTITY_ID.incrementAndGet();
                op.crackBlockX = next.getX();
                op.crackBlockY = next.getY();
                op.crackBlockZ = next.getZ();
                sendBreakAnimationPacket(player, op.crackEntityId, next, 5);
                return;
            }
            peekIndex++;
        }
    }

    private void flushOverflow(Player player, BreakingOperation op) {
        if (op.accumulatedOverflow.isEmpty()) return;
        DropHandler.flushOverflow(player, op.accumulatedOverflow, overflowHook);
        int count = op.accumulatedOverflow.stream().mapToInt(ItemStack::getAmount).sum();
        if (count > 0) {
            plugin.getMessageManager().send(player, "inventory-overflow",
                    Placeholder.unparsed("count", String.valueOf(count)));
        }
    }

    // ===== Gravity block freezing =====

    private void freezeBlocks(BreakingOperation op) {
        for (Block block : op.blocks) {
            frozenBlocks.add(block.getLocation());
        }
    }

    private void unfreezeBlocks(BreakingOperation op) {
        for (Block block : op.blocks) {
            frozenBlocks.remove(block.getLocation());
        }
    }

    /**
     * Flush accumulated overflow before the player's death drops resolve.
     * Without this, items that didn't fit the inventory mid-operation are
     * silently discarded on death. With OakOverflow the items go to storage
     * (surviving the death); otherwise they drop at the death location
     * alongside the regular death drops.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        BreakingOperation op = activeOperations.remove(event.getEntity().getUniqueId());
        if (op != null) {
            clearCrackAnimation(op);
            unfreezeBlocks(op);
            flushOverflow(event.getEntity(), op);
        }
    }

    /**
     * Flush accumulated overflow while the quitting player object is still
     * valid — the tick loop only sees the player as offline after the fact,
     * when there is no one left to give the items to.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        BreakingOperation op = activeOperations.remove(event.getPlayer().getUniqueId());
        if (op != null) {
            clearCrackAnimation(op);
            unfreezeBlocks(op);
            flushOverflow(event.getPlayer(), op);
        }
    }

    /**
     * Prevents gravity blocks (sand, gravel, etc.) from falling while they're
     * queued for animated breaking. Intercepts the block-to-falling-entity conversion
     * directly — more reliable than BlockPhysicsEvent for this purpose.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock
                && !frozenBlocks.isEmpty()
                && frozenBlocks.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ===== Crack animation =====

    private void clearCrackAnimation(BreakingOperation op) {
        if (!usePackets || op.crackEntityId == -1) return;

        Player player = Bukkit.getPlayer(op.playerId);
        if (player == null) return;

        // Send stage -1 to clear the animation
        sendBreakAnimationPacket(player, op.crackEntityId,
                op.crackBlockX, op.crackBlockY, op.crackBlockZ, -1);
        op.crackEntityId = -1;
    }

    private void sendBreakAnimationPacket(Player player, int entityId, Block block, int stage) {
        sendBreakAnimationPacket(player, entityId, block.getX(), block.getY(), block.getZ(), stage);
    }

    private void sendBreakAnimationPacket(Player player, int entityId, int x, int y, int z, int stage) {
        try {
            var position = new Vector3i(x, y, z);
            WrapperPlayServerBlockBreakAnimation packet = new WrapperPlayServerBlockBreakAnimation(
                    entityId, position, (byte) stage);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Exception e) {
            plugin.debug("[Breaking Debug] Failed to send crack animation: " + e.getMessage());
        }
    }

    // ===== Inner types =====

    private static class BreakingOperation {
        final UUID playerId;
        final ToolType toolType;
        final List<Block> blocks;
        final int tickInterval;
        final String progressMessageKey;
        final String completeMessageKey;
        final int totalBlocks;
        final boolean showProgress;

        int currentIndex = 0;
        int tickCounter = 0;
        int brokenCount = 0;
        final List<ItemStack> accumulatedOverflow = new ArrayList<>();

        // Crack animation tracking
        int crackEntityId = -1;
        int crackBlockX, crackBlockY, crackBlockZ;

        BreakingOperation(UUID playerId, ToolType toolType, List<Block> blocks, int tickInterval,
                          String progressMessageKey, String completeMessageKey, int totalBlocks,
                          boolean showProgress) {
            this.playerId = playerId;
            this.toolType = toolType;
            this.blocks = blocks;
            this.tickInterval = tickInterval;
            this.progressMessageKey = progressMessageKey;
            this.completeMessageKey = completeMessageKey;
            this.totalBlocks = totalBlocks;
            this.showProgress = showProgress;
        }
    }
}
