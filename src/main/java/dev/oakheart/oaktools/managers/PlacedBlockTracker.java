package dev.oakheart.oaktools.managers;

import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tracks player-placed log blocks in memory to distinguish them from naturally generated logs.
 * Uses a size-capped LinkedHashMap that evicts the oldest entries when full.
 * Lost on restart — after restart, all logs are treated as natural (same pattern as UltimateTimber).
 * StructureGrowEvent removes placed flags so player-grown trees are still choppable.
 */
public class PlacedBlockTracker implements Listener {

    private static final int MAX_TRACKED = 5000;

    private final Set<Location> placedLogs = newCappedSet(MAX_TRACKED);

    /**
     * Returns whether the block was placed by a player (and not yet broken or grown into a tree).
     */
    public boolean isPlayerPlaced(Block block) {
        return placedLogs.contains(block.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (Tag.LOGS.isTagged(event.getBlock().getType())) {
            placedLogs.add(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (Tag.LOGS.isTagged(event.getBlock().getType())) {
            placedLogs.remove(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        // When a sapling grows into a tree, remove the "placed" flag from all blocks
        // in the new structure so player-grown trees are still choppable
        for (var state : event.getBlocks()) {
            placedLogs.remove(state.getLocation());
        }
    }

    private static Set<Location> newCappedSet(int maxSize) {
        // LinkedHashMap with access order = false (insertion order), removeEldestEntry for cap
        Map<Location, Boolean> map = new LinkedHashMap<>(maxSize, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Location, Boolean> eldest) {
                return size() > maxSize;
            }
        };
        return java.util.Collections.newSetFromMap(map);
    }
}
