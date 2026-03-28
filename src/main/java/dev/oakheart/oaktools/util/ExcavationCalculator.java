package dev.oakheart.oaktools.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates the 3x3 grid of matching blocks on a given face for the Excavation Shovel.
 * Only includes blocks of the same material type as the center block.
 */
public class ExcavationCalculator {

    /**
     * Calculates blocks in an NxN grid on the given face, filtered to the same material
     * as the center block and shovel-mineable.
     *
     * @param center   the block that was clicked/targeted
     * @param face     the face of the block that was clicked
     * @param gridSize the grid size (3 = 3x3, 5 = 5x5, etc.)
     * @return list of matching blocks
     */
    public static List<Block> calculate(Block center, BlockFace face, int gridSize) {
        List<Block> blocks = new ArrayList<>();
        Material targetMaterial = center.getType();
        int radius = gridSize / 2;

        // Determine the two axes perpendicular to the face normal
        BlockFace[] axes = getPerpendicularAxes(face);
        BlockFace axis1 = axes[0];
        BlockFace axis2 = axes[1];

        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                Block target = center.getRelative(
                        axis1.getModX() * a + axis2.getModX() * b,
                        axis1.getModY() * a + axis2.getModY() * b,
                        axis1.getModZ() * a + axis2.getModZ() * b);

                if (target.getType() == targetMaterial) {
                    blocks.add(target);
                }
            }
        }

        return blocks;
    }

    private static BlockFace[] getPerpendicularAxes(BlockFace face) {
        return switch (face) {
            case UP, DOWN -> new BlockFace[]{BlockFace.NORTH, BlockFace.EAST};
            case NORTH, SOUTH -> new BlockFace[]{BlockFace.EAST, BlockFace.UP};
            case EAST, WEST -> new BlockFace[]{BlockFace.NORTH, BlockFace.UP};
            default -> new BlockFace[]{BlockFace.NORTH, BlockFace.EAST};
        };
    }
}
