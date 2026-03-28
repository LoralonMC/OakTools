package dev.oakheart.oaktools.util;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Ore grouping and vein detection for the Vein Miner tool.
 * Groups ore variants (e.g. iron_ore + deepslate_iron_ore) as the same vein.
 */
public class OreGrouping {

    private static final Map<Material, Set<Material>> GROUPS = new HashMap<>();

    static {
        group(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE);
        group(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);
        group(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE);
        group(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE);
        group(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE);
        group(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
        group(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
        group(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
        group(Material.NETHER_GOLD_ORE);
        group(Material.NETHER_QUARTZ_ORE);
        group(Material.ANCIENT_DEBRIS);
    }

    private static void group(Material... materials) {
        Set<Material> groupSet = Set.of(materials);
        for (Material mat : materials) {
            GROUPS.put(mat, groupSet);
        }
    }

    /**
     * Returns whether the material is a recognized ore type.
     */
    public static boolean isOre(Material material) {
        return GROUPS.containsKey(material);
    }

    /**
     * Returns the set of materials that count as the same vein as the given material.
     * If groupDeepslate is false, only returns the exact material.
     */
    public static Set<Material> getGroup(Material material, boolean groupDeepslate) {
        if (!groupDeepslate) {
            return Set.of(material);
        }
        return GROUPS.getOrDefault(material, Set.of(material));
    }

    /**
     * Finds all connected ore blocks in a vein using 6-connected BFS.
     *
     * @param startBlock     the ore block the player broke
     * @param maxBlocks      maximum number of blocks to return
     * @param groupDeepslate whether deepslate variants count as the same vein
     * @return list of connected ore blocks (excluding the start block)
     */
    public static List<Block> findVein(Block startBlock, int maxBlocks, boolean groupDeepslate) {
        Set<Material> targetMaterials = getGroup(startBlock.getType(), groupDeepslate);
        List<Block> vein = new ArrayList<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();

        queue.add(startBlock);
        visited.add(startBlock);

        while (!queue.isEmpty() && vein.size() < maxBlocks) {
            Block current = queue.poll();

            // Add to vein (skip the start block — vanilla handles it)
            if (!current.equals(startBlock)) {
                vein.add(current);
            }

            // 6-connected neighbors
            for (Block neighbor : getAdjacentBlocks(current)) {
                if (visited.contains(neighbor)) continue;
                visited.add(neighbor);

                if (targetMaterials.contains(neighbor.getType())) {
                    queue.add(neighbor);
                }
            }
        }

        return vein;
    }

    private static Block[] getAdjacentBlocks(Block block) {
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
