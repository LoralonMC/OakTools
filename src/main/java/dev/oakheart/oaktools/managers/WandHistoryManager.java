package dev.oakheart.oaktools.managers;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WandHistoryManager implements Listener {

    private final OakTools plugin;
    private final Map<UUID, Deque<WandOperation>> history = new HashMap<>();

    public WandHistoryManager(OakTools plugin) {
        this.plugin = plugin;
    }

    public void recordOperation(Player player, List<BlockSnapshot> snapshots, BlockData placedData,
                                int blockCount, boolean blocksConsumed) {
        if (!plugin.getConfigManager().isWandUndoEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Deque<WandOperation> deque = history.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        // Prune expired entries
        pruneExpired(deque);

        // Enforce max history depth
        int maxHistory = plugin.getConfigManager().getWandUndoMaxHistory();
        while (deque.size() >= maxHistory) {
            deque.removeLast();
        }

        deque.addFirst(new WandOperation(snapshots, placedData, blockCount, blocksConsumed, System.currentTimeMillis()));
    }

    public int undo(Player player) {
        UUID uuid = player.getUniqueId();
        Deque<WandOperation> deque = history.get(uuid);
        if (deque == null || deque.isEmpty()) {
            return 0;
        }

        // Prune expired entries
        pruneExpired(deque);

        if (deque.isEmpty()) {
            return 0;
        }

        WandOperation operation = deque.pollFirst();
        Material placedMaterial = operation.placedData().getMaterial();
        int restoredCount = 0;
        int returnedBlocks = 0;

        for (BlockSnapshot snapshot : operation.snapshots()) {
            Block block = snapshot.location().getBlock();

            // Safety check: only undo if the block still matches what we placed
            if (block.getType() != placedMaterial) {
                continue;
            }

            // Log removal to CoreProtect
            plugin.getCoreProtectLogger().logWandRemoval(player, block, block.getBlockData());

            // Restore original block data
            block.setBlockData(snapshot.originalData(), true);

            // Log placement of original to CoreProtect
            plugin.getCoreProtectLogger().logWandPlacement(player, block, snapshot.originalData());

            restoredCount++;
            if (operation.blocksConsumed()) {
                returnedBlocks++;
            }
        }

        // Return consumed blocks to inventory
        if (returnedBlocks > 0) {
            ItemStack returnStack = new ItemStack(placedMaterial, returnedBlocks);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(returnStack);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        if (deque.isEmpty()) {
            history.remove(uuid);
        }

        plugin.debug("[Wand Debug] Undid " + restoredCount + " blocks, returned " + returnedBlocks + " items");

        return restoredCount;
    }

    private void pruneExpired(Deque<WandOperation> deque) {
        long expireMillis = plugin.getConfigManager().getWandUndoExpireSeconds() * 1000L;
        long now = System.currentTimeMillis();

        while (!deque.isEmpty()) {
            WandOperation oldest = deque.peekLast();
            if (now - oldest.timestamp() > expireMillis) {
                deque.removeLast();
            } else {
                break;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        history.remove(event.getPlayer().getUniqueId());
    }

    public record BlockSnapshot(Location location, BlockData originalData) {}

    public record WandOperation(List<BlockSnapshot> snapshots, BlockData placedData,
                                int blockCount, boolean blocksConsumed, long timestamp) {}
}
