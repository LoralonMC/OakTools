package dev.oakheart.oaktools.util;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class WandPlacementCalculator {

    /**
     * Build face placement with full protection checks (used by WandListener for actual placement).
     * BFS only expands through source blocks whose face is exposed (target position is replaceable),
     * preventing placements from leaking around corners or behind obstructions.
     */
    public static List<Block> buildFacePlacement(Block clickedBlock, BlockFace clickedFace, Material sourceMaterial,
                                                  int maxBlocks, Player player, EquipmentSlot hand,
                                                  ItemStack tool, OakTools plugin) {
        // Fast-fail: check protection on the first potential placement block
        Block firstTarget = clickedBlock.getRelative(clickedFace);
        if (isReplaceable(firstTarget, plugin) &&
            !plugin.getProtectionService().canModifyBlock(player, firstTarget, hand, tool)) {
            return List.of();
        }

        List<Block> placements = new ArrayList<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();

        queue.add(clickedBlock);
        visited.add(clickedBlock);

        BlockFace[] neighborFaces = getFloodFillNeighborFaces(clickedFace);

        while (!queue.isEmpty() && placements.size() < maxBlocks) {
            Block current = queue.poll();

            Block target = current.getRelative(clickedFace);
            if (isReplaceable(target, plugin) &&
                plugin.getProtectionService().canModifyBlock(player, target, hand, tool)) {
                placements.add(target);
            }

            for (BlockFace face : neighborFaces) {
                Block neighbor = current.getRelative(face);
                if (visited.contains(neighbor)) {
                    continue;
                }
                visited.add(neighbor);

                // Only expand through source blocks whose face is exposed
                if (neighbor.getType() == sourceMaterial && isReplaceable(neighbor.getRelative(clickedFace), plugin)) {
                    queue.add(neighbor);
                }
            }
        }

        return placements;
    }

    /**
     * Build line placement with full protection checks (used by WandListener for actual placement).
     */
    public static List<Block> buildLinePlacement(Block clickedBlock, BlockFace clickedFace, Material sourceMaterial,
                                                  int maxBlocks, Player player, EquipmentSlot hand,
                                                  ItemStack tool, OakTools plugin) {
        // Fast-fail: check protection on the first potential placement block
        Block firstTarget = clickedBlock.getRelative(clickedFace);
        if (isReplaceable(firstTarget, plugin) &&
            !plugin.getProtectionService().canModifyBlock(player, firstTarget, hand, tool)) {
            return List.of();
        }

        List<Block> placements = new ArrayList<>();

        BlockFace lineDirection = calculateLineDirection(clickedFace, player);
        Block current = clickedBlock.getRelative(clickedFace);

        for (int i = 0; i < maxBlocks; i++) {
            if (!isReplaceable(current, plugin)) {
                break;
            }

            if (!plugin.getProtectionService().canModifyBlock(player, current, hand, tool)) {
                break;
            }

            placements.add(current);
            current = current.getRelative(lineDirection);
        }

        return placements;
    }

    /**
     * Build face placement preview without protection checks (used by preview system).
     * Same exposed-face constraint as the full version.
     */
    public static List<Block> buildFacePlacementPreview(Block clickedBlock, BlockFace clickedFace,
                                                         Material sourceMaterial, int maxBlocks, OakTools plugin) {
        List<Block> placements = new ArrayList<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();

        queue.add(clickedBlock);
        visited.add(clickedBlock);

        BlockFace[] neighborFaces = getFloodFillNeighborFaces(clickedFace);

        while (!queue.isEmpty() && placements.size() < maxBlocks) {
            Block current = queue.poll();

            Block target = current.getRelative(clickedFace);
            if (isReplaceable(target, plugin)) {
                placements.add(target);
            }

            for (BlockFace face : neighborFaces) {
                Block neighbor = current.getRelative(face);
                if (visited.contains(neighbor)) {
                    continue;
                }
                visited.add(neighbor);

                if (neighbor.getType() == sourceMaterial && isReplaceable(neighbor.getRelative(clickedFace), plugin)) {
                    queue.add(neighbor);
                }
            }
        }

        return placements;
    }

    /**
     * Build line placement preview without protection checks (used by preview system).
     */
    public static List<Block> buildLinePlacementPreview(Block clickedBlock, BlockFace clickedFace,
                                                         Material sourceMaterial, int maxBlocks,
                                                         Player player, OakTools plugin) {
        List<Block> placements = new ArrayList<>();

        BlockFace lineDirection = calculateLineDirection(clickedFace, player);
        Block current = clickedBlock.getRelative(clickedFace);

        for (int i = 0; i < maxBlocks; i++) {
            if (!isReplaceable(current, plugin)) {
                break;
            }

            placements.add(current);
            current = current.getRelative(lineDirection);
        }

        return placements;
    }

    public static BlockFace calculateLineDirection(BlockFace clickedFace, Player player) {
        if (clickedFace == BlockFace.UP || clickedFace == BlockFace.DOWN) {
            return player.getFacing();
        }

        float pitch = player.getLocation().getPitch();
        if (Math.abs(pitch) > 45.0f) {
            return pitch > 0 ? BlockFace.DOWN : BlockFace.UP;
        }

        return player.getFacing();
    }

    public static BlockFace[] getFloodFillNeighborFaces(BlockFace clickedFace) {
        return switch (clickedFace) {
            case UP, DOWN -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            case NORTH, SOUTH -> new BlockFace[]{BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
            case EAST, WEST -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.UP, BlockFace.DOWN};
            default -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        };
    }

    public static boolean isReplaceable(Block block, OakTools plugin) {
        Material type = block.getType();
        return type.isAir() || type == Material.WATER || type == Material.LAVA ||
               plugin.getConfigManager().getReplaceableMaterials().contains(type);
    }
}
