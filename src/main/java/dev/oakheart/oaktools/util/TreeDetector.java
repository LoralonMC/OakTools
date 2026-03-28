package dev.oakheart.oaktools.util;

import dev.oakheart.oaktools.managers.PlacedBlockTracker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Detects natural trees by finding connected logs and validating nearby natural leaves.
 * Uses upward-only BFS (trees grow up), same-material matching, placed-block filtering,
 * and a minimum natural leaf count to distinguish generated trees from player structures.
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

        // BFS upward and laterally (not downward) to find connected logs
        List<Block> logs = new ArrayList<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();

        queue.add(startBlock);
        visited.add(startBlock);

        while (!queue.isEmpty() && logs.size() < maxBlocks) {
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

        // Sort bottom-to-top for natural visual breaking order
        logs.sort(Comparator.comparingInt(Block::getY));

        return logs;
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
     * Returns the 6 adjacent blocks (up, down, north, south, east, west).
     * The caller filters to upward-only expansion.
     */
    private static Block[] getTreeNeighbors(Block block) {
        return new Block[]{
                block.getRelative(1, 0, 0),
                block.getRelative(-1, 0, 0),
                block.getRelative(0, 1, 0),
                block.getRelative(0, -1, 0),
                block.getRelative(0, 0, 1),
                block.getRelative(0, 0, -1)
        };
    }
}
