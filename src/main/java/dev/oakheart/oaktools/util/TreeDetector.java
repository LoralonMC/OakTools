package dev.oakheart.oaktools.util;

import dev.oakheart.oaktools.managers.PlacedBlockTracker;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * Detects natural trees by finding connected logs and validating nearby natural leaves.
 * Expands nearest-tree-first (horizontal distance from the start trunk, then height) so
 * connected walls of trees are completed one tree at a time, uses same-material matching,
 * placed-block filtering, and a minimum natural leaf count to distinguish generated trees
 * from player structures. Logs whose removal would orphan an out-of-cap treetop are
 * trimmed from the result so boundary trees are left standing whole.
 */
public class TreeDetector {

    /**
     * Detects a natural tree starting from the given log block.
     *
     * @param startBlock the log block the player broke
     * @param maxBlocks  maximum number of log blocks to return
     * @param minLeaves  minimum natural leaves required to qualify as a generated tree
     * @param tracker    placed block tracker (may be null — all logs treated as natural)
     * @return list of connected log blocks sorted bottom-to-top (empty if not a valid tree)
     */
    public static List<Block> detectTree(Block startBlock, int maxBlocks, int minLeaves,
                                          PlacedBlockTracker tracker) {
        Material logType = startBlock.getType();

        if (!isNaturalLog(logType)) {
            return List.of();
        }

        // Skip if the start block was player-placed
        if (tracker != null && tracker.isPlayerPlaced(startBlock)) {
            return List.of();
        }

        // Nearest-tree-first expansion: pop by horizontal distance from the
        // start trunk, then by height. Plain FIFO order spreads sideways into
        // neighbouring trunks (walls of trees) before finishing the canopy of
        // the tree that was hit, so the block cap orphaned every treetop
        // instead of completing trees one at a time.
        final int startX = startBlock.getX();
        final int startZ = startBlock.getZ();
        Comparator<Block> nearestFirst = Comparator
                .comparingInt((Block b) -> Math.max(Math.abs(b.getX() - startX), Math.abs(b.getZ() - startZ)))
                .thenComparingInt(Block::getY);

        List<Block> logs = new ArrayList<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new PriorityQueue<>(nearestFirst);

        queue.add(startBlock);
        visited.add(startBlock);

        while (!queue.isEmpty() && logs.size() < maxBlocks - 1) { // -1 to account for the initial block
            Block current = queue.poll();

            // Add to results (skip start block — vanilla handles it)
            if (!current.equals(startBlock)) {
                logs.add(current);
            }

            // Search all 6 adjacent blocks — branches can extend in any direction
            // (leaf validation prevents false positives on player structures)
            for (Block neighbor : getTreeNeighbors(current)) {
                if (visited.contains(neighbor)) continue;
                visited.add(neighbor);

                if (neighbor.getType() != logType) continue;

                // Skip player-placed logs
                if (tracker != null && tracker.isPlayerPlaced(neighbor)) continue;

                queue.add(neighbor);
            }
        }

        // Validate: count natural leaves near the detected logs
        Set<Block> allLogs = new HashSet<>(logs);
        allLogs.add(startBlock);
        int naturalLeaves = countNaturalLeaves(allLogs);

        if (naturalLeaves < minLeaves) {
            return List.of();
        }

        trimFloatingTops(logs, startBlock, logType, tracker);

        // Nearest tree first, bottom-to-top within it, so each tree finishes
        // before the breaking animation moves to the next.
        logs.sort(nearestFirst);

        return logs;
    }

    /** Max blocks flooded when checking whether a left-behind log has its own
     *  support path; anything larger is assumed attached to a real structure. */
    private static final int SUPPORT_SEARCH_LIMIT = 256;

    /**
     * Removes logs from the break list whose removal would orphan a natural
     * log the block cap left out (a trunk cut short mid-air). Cascades
     * downward, so a boundary tree that doesn't fully fit the cap is left
     * standing whole instead of having its top float. Player-placed logs
     * above don't count — those are the placer's artifact, not a treetop.
     * The start block is never trimmed (vanilla breaks it regardless).
     */
    private static void trimFloatingTops(List<Block> logs, Block startBlock, Material logType,
                                         PlacedBlockTracker tracker) {
        Set<Block> breaking = new HashSet<>(logs);
        breaking.add(startBlock);
        Set<Block> knownSupported = new HashSet<>();

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Block log : logs) {
                if (wouldOrphanNeighbor(log, logType, breaking, knownSupported, tracker)) {
                    // Leave this log standing so the log above keeps support.
                    logs.remove(log);
                    breaking.remove(log);
                    changed = true;
                    break;
                }
            }
        }
    }

    /**
     * Whether breaking this log would leave an undetected natural log beside
     * or above it with no support path of its own. Same-level neighbours are
     * included because big-oak branches extend horizontally from the trunk.
     */
    private static boolean wouldOrphanNeighbor(Block log, Material logType, Set<Block> breaking,
                                          Set<Block> knownSupported, PlacedBlockTracker tracker) {
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dy == 0 && dx == 0 && dz == 0) continue;
                    Block neighbor = log.getRelative(dx, dy, dz);
                    if (neighbor.getType() != logType) continue;
                    if (breaking.contains(neighbor)) continue;
                    if (tracker != null && tracker.isPlayerPlaced(neighbor)) continue;
                    if (hasOwnSupport(neighbor, logType, breaking, knownSupported)) continue;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a log that stays in the world keeps a support path that doesn't
     * run through blocks scheduled for breaking: flood through its connected
     * unbroken same-type logs looking for one resting on an unbroken solid
     * block (leaves don't count — they decay). A neighbouring standing tree
     * passes via its own trunk; a treetop whose only trunk is being felled
     * does not.
     */
    private static boolean hasOwnSupport(Block start, Material logType, Set<Block> breaking,
                                         Set<Block> knownSupported) {
        if (knownSupported.contains(start)) {
            return true;
        }

        Set<Block> component = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        queue.add(start);
        component.add(start);

        while (!queue.isEmpty()) {
            Block current = queue.poll();

            // An unbroken same-type log below is part of this component (its
            // own support gets checked when the flood reaches it), not ground.
            Block below = current.getRelative(BlockFace.DOWN);
            boolean belowIsComponentLog = below.getType() == logType && !breaking.contains(below);
            if (!belowIsComponentLog && !breaking.contains(below) && below.getType().isSolid()
                    && !Tag.LEAVES.isTagged(below.getType())) {
                knownSupported.addAll(component);
                return true;
            }

            if (component.size() >= SUPPORT_SEARCH_LIMIT) {
                knownSupported.addAll(component);
                return true;
            }

            for (Block neighbor : getTreeNeighbors(current)) {
                if (component.contains(neighbor)) continue;
                if (neighbor.getType() != logType) continue;
                if (breaking.contains(neighbor)) continue;
                component.add(neighbor);
                queue.add(neighbor);
            }
        }

        return false;
    }

    /**
     * Checks if the material is a natural (non-stripped, non-wood) log.
     */
    public static boolean isNaturalLog(Material material) {
        String name = material.name();
        return (name.endsWith("_LOG") || name.endsWith("_STEM"))
                && !name.startsWith("STRIPPED_");
    }

    /**
     * Counts natural (non-persistent) leaves within 2 blocks of any log in the set.
     */
    private static int countNaturalLeaves(Set<Block> logs) {
        Set<Block> checkedLeaves = new HashSet<>();
        int count = 0;

        for (Block log : logs) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        Block candidate = log.getRelative(dx, dy, dz);
                        if (checkedLeaves.contains(candidate)) continue;
                        checkedLeaves.add(candidate);

                        if (candidate.getBlockData() instanceof Leaves leaves) {
                            if (!leaves.isPersistent()) {
                                count++;
                            }
                        }
                    }
                }
            }
        }

        return count;
    }

    /**
     * Returns all 26 surrounding blocks (face + edge + corner neighbors).
     * Trees like acacia have diagonal log connections.
     */
    private static List<Block> getTreeNeighbors(Block block) {
        List<Block> neighbors = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    neighbors.add(block.getRelative(dx, dy, dz));
                }
            }
        }
        return neighbors;
    }
}
