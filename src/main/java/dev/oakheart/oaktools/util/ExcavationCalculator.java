package dev.oakheart.oaktools.util;

import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates the 3x3 grid of shovel-mineable blocks on a given face.
 * Shared by both the preview system and the excavator listener.
 */
public class ExcavationCalculator {

    /**
     * Calculates blocks in a 3x3 grid on the given face, filtered to shovel-mineable blocks.
     *
     * @param center    the block that was clicked/targeted
     * @param face      the face of the block that was clicked
     * @param maxBlocks maximum number of blocks to return
     * @return list of valid shovel-mineable blocks (may be less than 9 if some aren't mineable)
     */
    public static List<Block> calculate(Block center, BlockFace face, int maxBlocks) {
        List<Block> blocks = new ArrayList<>();

        // Determine the two axes perpendicular to the face normal
        BlockFace[] axes = getPerpendicularAxes(face);
        BlockFace axis1 = axes[0];
        BlockFace axis2 = axes[1];

        for (int a = -1; a <= 1 && blocks.size() < maxBlocks; a++) {
            for (int b = -1; b <= 1 && blocks.size() < maxBlocks; b++) {
                Block target = center.getRelative(
                        axis1.getModX() * a + axis2.getModX() * b,
                        axis1.getModY() * a + axis2.getModY() * b,
                        axis1.getModZ() * a + axis2.getModZ() * b);

                if (Tag.MINEABLE_SHOVEL.isTagged(target.getType())) {
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
