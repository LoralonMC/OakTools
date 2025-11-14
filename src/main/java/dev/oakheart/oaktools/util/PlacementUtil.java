package dev.oakheart.oaktools.util;

import org.bukkit.Axis;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Utility class for vanilla-accurate block placement logic.
 */
public class PlacementUtil {

    /**
     * Apply vanilla-like placement logic to block data.
     *
     * @param blockData the block data to modify
     * @param clickedFace the face that was clicked
     * @param clickedLocation the exact location clicked (for slab/stair positioning)
     * @param player the player placing the block
     * @return the modified block data
     */
    public static BlockData applyPlacementLogic(BlockData blockData, BlockFace clickedFace, Vector clickedLocation, Player player) {
        // Handle directional blocks (furnaces, hoppers, dispensers, etc.)
        if (blockData instanceof Directional directional) {
            // Face opposite to clicked face (toward player)
            BlockFace placementFace = clickedFace.getOppositeFace();

            // Ensure the face is valid for this block
            if (directional.getFaces().contains(placementFace)) {
                directional.setFacing(placementFace);
            }
        }

        // Handle orientable blocks (logs, pillars, bone blocks)
        if (blockData instanceof Orientable orientable) {
            Axis axis = switch (clickedFace) {
                case UP, DOWN -> Axis.Y;
                case NORTH, SOUTH -> Axis.Z;
                case EAST, WEST -> Axis.X;
                default -> Axis.Y;
            };
            orientable.setAxis(axis);
        }

        // Handle slabs (top/bottom based on click position)
        if (blockData instanceof Slab slab) {
            if (clickedFace == BlockFace.DOWN) {
                slab.setType(Slab.Type.TOP);
            } else if (clickedFace == BlockFace.UP) {
                slab.setType(Slab.Type.BOTTOM);
            } else {
                // Clicked on side - check click height
                double clickedY = clickedLocation.getY() % 1.0;
                if (clickedY < 0) clickedY += 1.0; // Handle negative coords

                if (clickedY >= 0.5) {
                    slab.setType(Slab.Type.TOP);
                } else {
                    slab.setType(Slab.Type.BOTTOM);
                }
            }
        }

        // Handle stairs (facing based on player direction, half based on click position)
        if (blockData instanceof Stairs stairs) {
            // Set facing based on player's direction (like vanilla)
            BlockFace playerFacing = player.getFacing();
            if (stairs.getFaces().contains(playerFacing)) {
                stairs.setFacing(playerFacing);
            }

            // Set half based on click position
            if (clickedFace == BlockFace.DOWN) {
                stairs.setHalf(Stairs.Half.TOP);
            } else if (clickedFace == BlockFace.UP) {
                stairs.setHalf(Stairs.Half.BOTTOM);
            } else {
                // Clicked on side - check click height
                double clickedY = clickedLocation.getY() % 1.0;
                if (clickedY < 0) clickedY += 1.0;

                if (clickedY >= 0.5) {
                    stairs.setHalf(Stairs.Half.TOP);
                } else {
                    stairs.setHalf(Stairs.Half.BOTTOM);
                }
            }
        }

        return blockData;
    }
}
