package dev.oakheart.oaktools.util;

import org.bukkit.Axis;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Utility class for block state manipulation.
 */
public class BlockUtil {

    /**
     * Check if a block has multiple facing properties (fences, glass panes, iron bars).
     *
     * @param block the block to check
     * @return true if the block has multiple facing properties
     */
    public static boolean hasMultipleFacing(Block block) {
        return block.getBlockData() instanceof MultipleFacing;
    }

    /**
     * Check if a block is a wall.
     *
     * @param block the block to check
     * @return true if the block is a wall
     */
    public static boolean isWall(Block block) {
        return block.getBlockData() instanceof Wall;
    }

    /**
     * Check if a block is stairs.
     *
     * @param block the block to check
     * @return true if the block is stairs
     */
    public static boolean isStairs(Block block) {
        return block.getBlockData() instanceof Stairs;
    }

    /**
     * Check if a block is waterloggable.
     *
     * @param block the block to check
     * @return true if the block is waterloggable
     */
    public static boolean isWaterloggable(Block block) {
        return block.getBlockData() instanceof Waterlogged;
    }

    /**
     * Check if a block is directional.
     *
     * @param block the block to check
     * @return true if the block is directional
     */
    public static boolean isDirectional(Block block) {
        return block.getBlockData() instanceof Directional;
    }

    /**
     * Check if a block has an axis (logs, pillars, bone blocks).
     *
     * @param block the block to check
     * @return true if the block has an axis
     */
    public static boolean hasAxis(Block block) {
        return block.getBlockData() instanceof Orientable;
    }

    /**
     * Check if a block is a slab.
     *
     * @param block the block to check
     * @return true if the block is a slab
     */
    public static boolean isSlab(Block block) {
        return block.getBlockData() instanceof Slab;
    }

    /**
     * Toggle a specific face on a multiple facing block (fences, glass panes, iron bars).
     *
     * @param block the block to modify
     * @param clickedFace the face that was clicked
     * @param interactionPoint the exact point where the block was clicked (can be null)
     * @param playerFacing the direction the player is facing (used as fallback)
     * @return true if successful
     */
    public static boolean cycleMultipleFacing(Block block, BlockFace clickedFace, org.bukkit.util.Vector interactionPoint, BlockFace playerFacing) {
        if (!(block.getBlockData() instanceof MultipleFacing multipleFacing)) {
            return false;
        }

        // Get all possible faces for this block type
        var allowedFaces = multipleFacing.getAllowedFaces();
        if (allowedFaces.isEmpty()) {
            return false;
        }

        // Always use interaction point to determine which face to toggle
        BlockFace faceToToggle;
        if (interactionPoint != null) {
            faceToToggle = getClosestHorizontalFace(block, interactionPoint);
        } else {
            // Fallback: use clicked face if available, otherwise player facing
            faceToToggle = switch (clickedFace) {
                case NORTH, SOUTH, EAST, WEST -> clickedFace;
                case UP, DOWN -> playerFacing;
                default -> playerFacing;
            };
        }

        if (faceToToggle != null && allowedFaces.contains(faceToToggle)) {
            boolean currentState = multipleFacing.hasFace(faceToToggle);
            multipleFacing.setFace(faceToToggle, !currentState);
            block.setBlockData(multipleFacing, false);
            return true;
        }

        return false;
    }

    /**
     * Get the closest horizontal face based on where the player clicked on a block.
     *
     * @param block the block
     * @param interactionPoint the point where the block was clicked
     * @return the closest horizontal BlockFace (NORTH, SOUTH, EAST, or WEST)
     */
    private static BlockFace getClosestHorizontalFace(Block block, org.bukkit.util.Vector interactionPoint) {
        // Get the relative position within the block (0.0 to 1.0)
        double x = interactionPoint.getX() - block.getX();
        double z = interactionPoint.getZ() - block.getZ();

        // Center the coordinates (-0.5 to 0.5)
        double relX = x - 0.5;
        double relZ = z - 0.5;

        // Determine which axis is stronger
        if (Math.abs(relX) > Math.abs(relZ)) {
            return relX > 0 ? BlockFace.EAST : BlockFace.WEST;
        } else {
            return relZ > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
    }

    /**
     * Cycle wall connections for a specific face.
     *
     * @param block the block to modify
     * @param clickedFace the face that was clicked
     * @param interactionPoint the exact point where the block was clicked (can be null)
     * @param playerFacing the direction the player is facing (used as fallback)
     * @return true if successful
     */
    public static boolean cycleWall(Block block, BlockFace clickedFace, org.bukkit.util.Vector interactionPoint, BlockFace playerFacing) {
        if (!(block.getBlockData() instanceof Wall wall)) {
            return false;
        }

        // Always use interaction point to determine which side to modify
        BlockFace sideToModify;
        if (interactionPoint != null) {
            sideToModify = getClosestHorizontalFace(block, interactionPoint);
        } else {
            // Fallback: use clicked face if available, otherwise player facing
            sideToModify = switch (clickedFace) {
                case NORTH, SOUTH, EAST, WEST -> clickedFace;
                case UP, DOWN -> playerFacing;
                default -> playerFacing;
            };
        }

        // Ensure we have a valid horizontal face
        if (sideToModify != BlockFace.NORTH && sideToModify != BlockFace.SOUTH &&
            sideToModify != BlockFace.EAST && sideToModify != BlockFace.WEST) {
            return false;
        }

        // Cycle the height of the determined side
        Wall.Height currentHeight = wall.getHeight(sideToModify);
        Wall.Height nextHeight = switch (currentHeight) {
            case NONE -> Wall.Height.LOW;
            case LOW -> Wall.Height.TALL;
            case TALL -> Wall.Height.NONE;
        };

        // Only modify if the height actually changes
        if (currentHeight == nextHeight) {
            return false;
        }

        wall.setHeight(sideToModify, nextHeight);

        // Recalculate the "up" (center post) property based on new connections.
        // Vanilla rule: up=false only when exactly 2 collinear connections of the same height.
        wall.setUp(shouldWallBeUp(wall));

        block.setBlockData(wall, false);
        return true;
    }

    /**
     * Determine whether a wall's center post ("up") should be visible.
     * In vanilla, the post is hidden only when the wall has exactly two collinear
     * connections (N+S or E+W) of the same height.
     *
     * @param wall the wall block data
     * @return true if the center post should be shown
     */
    private static boolean shouldWallBeUp(Wall wall) {
        Wall.Height north = wall.getHeight(BlockFace.NORTH);
        Wall.Height south = wall.getHeight(BlockFace.SOUTH);
        Wall.Height east = wall.getHeight(BlockFace.EAST);
        Wall.Height west = wall.getHeight(BlockFace.WEST);

        boolean hasNorth = north != Wall.Height.NONE;
        boolean hasSouth = south != Wall.Height.NONE;
        boolean hasEast = east != Wall.Height.NONE;
        boolean hasWest = west != Wall.Height.NONE;

        int connectionCount = (hasNorth ? 1 : 0) + (hasSouth ? 1 : 0) + (hasEast ? 1 : 0) + (hasWest ? 1 : 0);

        // Post hidden only with exactly 2 collinear same-height connections
        if (connectionCount == 2) {
            if (hasNorth && hasSouth && !hasEast && !hasWest && north == south) {
                return false;
            }
            if (hasEast && hasWest && !hasNorth && !hasSouth && east == west) {
                return false;
            }
        }

        return true;
    }

    /**
     * Edit stairs shape and facing based on cursor position.
     * Does NOT modify the half (TOP/BOTTOM).
     *
     * @param block the stairs block to edit
     * @param clickedFace the face that was clicked
     * @param interactionPoint the exact point where the block was clicked (can be null)
     * @param playerPos the player's position (used to determine viewing angle)
     * @param debug whether to log debug messages
     * @return true if successful
     */
    public static boolean editStairsShape(Block block, BlockFace clickedFace, org.bukkit.util.Vector interactionPoint, org.bukkit.util.Vector playerPos, boolean debug) {
        if (!(block.getBlockData() instanceof Stairs stairs)) {
            return false;
        }

        // Intelligently determine facing and shape based on cursor position
        if (interactionPoint != null) {
            StairsConfig config = determineStairsConfig(block, interactionPoint, clickedFace, playerPos, debug);

            boolean changed = false;
            if (config.facing != stairs.getFacing()) {
                stairs.setFacing(config.facing);
                changed = true;
            }
            if (config.shape != stairs.getShape()) {
                stairs.setShape(config.shape);
                changed = true;
            }

            if (changed) {
                block.setBlockData(stairs, false);
                return true;
            }
            return false;
        } else {
            // Fallback: cycle through shapes
            Stairs.Shape currentShape = stairs.getShape();
            Stairs.Shape nextShape = switch (currentShape) {
                case STRAIGHT -> Stairs.Shape.INNER_LEFT;
                case INNER_LEFT -> Stairs.Shape.INNER_RIGHT;
                case INNER_RIGHT -> Stairs.Shape.OUTER_LEFT;
                case OUTER_LEFT -> Stairs.Shape.OUTER_RIGHT;
                case OUTER_RIGHT -> Stairs.Shape.STRAIGHT;
            };
            stairs.setShape(nextShape);
            block.setBlockData(stairs, false);
            return true;
        }
    }

    /**
     * Helper class to store stairs configuration.
     */
    private static class StairsConfig {
        BlockFace facing;
        Stairs.Shape shape;

        StairsConfig(BlockFace facing, Stairs.Shape shape) {
            this.facing = facing;
            this.shape = shape;
        }
    }

    /**
     * Corner position enum for octant detection.
     */
    private enum Corner {
        SOUTH_WEST, NORTH_WEST, NORTH_EAST, SOUTH_EAST
    }

    /**
     * Octant represents which eighth of a block was clicked.
     */
    private static class Octant {
        Corner corner;
        boolean isTop;

        Octant(Corner corner, boolean isTop) {
            this.corner = corner;
            this.isTop = isTop;
        }
    }

    /**
     * Determine stairs facing and shape based on cursor position using corner-toggle logic.
     * Clicking a corner toggles it between raised and lowered.
     *
     * @param block the stairs block
     * @param interactionPoint the exact cursor position
     * @param clickedFace the face that was clicked
     * @param playerPos the player's position
     * @param debug whether to log debug messages
     * @return configuration with facing and shape
     */
    private static StairsConfig determineStairsConfig(Block block, org.bukkit.util.Vector interactionPoint, BlockFace clickedFace, org.bukkit.util.Vector playerPos, boolean debug) {
        if (!(block.getBlockData() instanceof Stairs stairs)) {
            return new StairsConfig(BlockFace.NORTH, Stairs.Shape.STRAIGHT);
        }

        // Store current facing for edge case handling
        BlockFace currentFacing = stairs.getFacing();

        // Detect which octant was clicked, considering the clicked face for better accuracy
        Octant octant = detectOctant(block, interactionPoint, clickedFace, playerPos);
        Corner clickedCorner = octant.corner;

        if (debug) {
            org.bukkit.Bukkit.getLogger().info(String.format(
                "[File Debug] Stairs click - Corner: %s, IsTop: %s, Face: %s, Current: %s %s, Click: %.2f,%.2f,%.2f, Player: %.2f,%.2f",
                clickedCorner, octant.isTop, clickedFace, stairs.getFacing(), stairs.getShape(),
                interactionPoint.getX() % 1, interactionPoint.getY() % 1, interactionPoint.getZ() % 1,
                playerPos.getX(), playerPos.getZ()
            ));
        }

        // Get which corners are currently raised
        boolean[] raisedCorners = getRaisedCorners(stairs.getFacing(), stairs.getShape());

        if (debug) {
            org.bukkit.Bukkit.getLogger().info(String.format(
                "[File Debug] Current raised: SW=%s NW=%s NE=%s SE=%s",
                raisedCorners[0], raisedCorners[1], raisedCorners[2], raisedCorners[3]
            ));
        }

        // Toggle the clicked corner
        int cornerIndex = getCornerIndex(clickedCorner);
        raisedCorners[cornerIndex] = !raisedCorners[cornerIndex];

        if (debug) {
            org.bukkit.Bukkit.getLogger().info(String.format(
                "[File Debug] New raised: SW=%s NW=%s NE=%s SE=%s",
                raisedCorners[0], raisedCorners[1], raisedCorners[2], raisedCorners[3]
            ));
        }

        // Calculate new facing and shape based on which corners are raised
        StairsConfig result = calculateStairsFromCorners(raisedCorners, currentFacing);

        if (debug) {
            org.bukkit.Bukkit.getLogger().info(String.format(
                "[File Debug] Result: %s %s", result.facing, result.shape
            ));
        }

        return result;
    }

    /**
     * Get which corners are currently raised for the given stairs configuration.
     * Returns array: [SW, NW, NE, SE]
     *
     * For NORTH-facing stairs:
     * - NORTH side (NW, NE) is the tall/back end (raised)
     * - SOUTH side (SW, SE) is the short/front end (lowered)
     */
    private static boolean[] getRaisedCorners(BlockFace facing, Stairs.Shape shape) {
        boolean[] raised = new boolean[4]; // SW, NW, NE, SE

        // First, determine raised corners in north reference frame
        // For north-facing stairs, north corners (NW, NE) are the tall end
        switch (shape) {
            case STRAIGHT -> {
                // Two back corners raised (north side for north-facing stairs)
                raised[1] = true;  // NW - raised
                raised[2] = true;  // NE - raised
            }
            case INNER_LEFT -> {
                // Three corners: back two + front left
                raised[0] = true;  // SW - raised (front left)
                raised[1] = true;  // NW - raised (back left)
                raised[2] = true;  // NE - raised (back right)
            }
            case INNER_RIGHT -> {
                // Three corners: back two + front right
                raised[1] = true;  // NW - raised (back left)
                raised[2] = true;  // NE - raised (back right)
                raised[3] = true;  // SE - raised (front right)
            }
            case OUTER_LEFT -> {
                // One corner: back left
                raised[1] = true;  // NW - raised
            }
            case OUTER_RIGHT -> {
                // One corner: back right
                raised[2] = true;  // NE - raised
            }
        }

        // Rotate corners based on facing direction
        return rotateRaisedCorners(raised, facing);
    }

    /**
     * Rotate raised corners array based on facing direction.
     * Clockwise rotation: NORTH(0°) -> EAST(90°) -> SOUTH(180°) -> WEST(270°)
     */
    private static boolean[] rotateRaisedCorners(boolean[] corners, BlockFace facing) {
        int rotations = switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;  // 90° clockwise from north
            case SOUTH -> 2; // 180° from north
            case WEST -> 3;  // 270° clockwise from north
            default -> 0;
        };

        boolean[] result = corners.clone();
        for (int i = 0; i < rotations; i++) {
            result = rotateRaisedCornersClockwise(result);
        }
        return result;
    }

    /**
     * Rotate raised corners array 90 degrees clockwise.
     * Looking from above, corners rotate: NW->NE, NE->SE, SE->SW, SW->NW
     * Array: [SW, NW, NE, SE] -> [SE, SW, NW, NE]
     */
    private static boolean[] rotateRaisedCornersClockwise(boolean[] corners) {
        return new boolean[] {
            corners[3],  // SW position gets SE value
            corners[0],  // NW position gets SW value
            corners[1],  // NE position gets NW value
            corners[2]   // SE position gets NE value
        };
    }

    /**
     * Get the index for a corner in the raised corners array.
     */
    private static int getCornerIndex(Corner corner) {
        return switch (corner) {
            case SOUTH_WEST -> 0;
            case NORTH_WEST -> 1;
            case NORTH_EAST -> 2;
            case SOUTH_EAST -> 3;
        };
    }

    /**
     * Calculate stairs facing and shape based on which corners are raised.
     * Array indices: [SW, NW, NE, SE]
     *
     * For reference, NORTH-facing stairs have:
     * - NW, NE raised (back/tall end)
     * - SW, SE lowered (front/short end)
     *
     * @param raised array of which corners are raised
     * @param currentFacing the current facing direction (used when count == 0)
     * @return configuration with facing and shape
     */
    private static StairsConfig calculateStairsFromCorners(boolean[] raised, BlockFace currentFacing) {
        // Count raised corners
        int count = 0;
        for (boolean r : raised) {
            if (r) count++;
        }

        // Determine configuration based on pattern
        if (count == 0) {
            // No corners raised - preserve current facing and default to STRAIGHT
            return new StairsConfig(currentFacing, Stairs.Shape.STRAIGHT);
        }

        if (count == 1) {
            // One corner - OUTER shape
            // Each corner corresponds to one facing direction for OUTER_LEFT
            if (raised[0]) return new StairsConfig(BlockFace.WEST, Stairs.Shape.OUTER_LEFT);    // SW
            if (raised[1]) return new StairsConfig(BlockFace.NORTH, Stairs.Shape.OUTER_LEFT);   // NW
            if (raised[2]) return new StairsConfig(BlockFace.EAST, Stairs.Shape.OUTER_LEFT);    // NE
            if (raised[3]) return new StairsConfig(BlockFace.SOUTH, Stairs.Shape.OUTER_LEFT);   // SE
        }

        if (count == 2) {
            // Two corners - STRAIGHT (if same edge)
            if (raised[0] && raised[3]) return new StairsConfig(BlockFace.SOUTH, Stairs.Shape.STRAIGHT);  // SW + SE (south edge)
            if (raised[1] && raised[2]) return new StairsConfig(BlockFace.NORTH, Stairs.Shape.STRAIGHT);  // NW + NE (north edge)
            if (raised[0] && raised[1]) return new StairsConfig(BlockFace.WEST, Stairs.Shape.STRAIGHT);   // SW + NW (west edge)
            if (raised[2] && raised[3]) return new StairsConfig(BlockFace.EAST, Stairs.Shape.STRAIGHT);   // NE + SE (east edge)

            // Diagonal corners - pick a reasonable default
            if (raised[0] && raised[2]) return new StairsConfig(BlockFace.NORTH, Stairs.Shape.STRAIGHT);  // SW + NE diagonal
            if (raised[1] && raised[3]) return new StairsConfig(BlockFace.NORTH, Stairs.Shape.STRAIGHT);  // NW + SE diagonal
        }

        if (count == 3) {
            // Three corners - INNER shape
            // Based on rotation: NORTH INNER_RIGHT has NW, NE, SE (missing SW)
            // Rotating gives: EAST(missing NW), SOUTH(missing NE), WEST(missing SE)
            if (!raised[0]) return new StairsConfig(BlockFace.NORTH, Stairs.Shape.INNER_RIGHT); // Missing SW
            if (!raised[1]) return new StairsConfig(BlockFace.EAST, Stairs.Shape.INNER_RIGHT);  // Missing NW
            if (!raised[2]) return new StairsConfig(BlockFace.SOUTH, Stairs.Shape.INNER_RIGHT); // Missing NE
            if (!raised[3]) return new StairsConfig(BlockFace.WEST, Stairs.Shape.INNER_RIGHT);  // Missing SE
        }

        if (count == 4) {
            // All corners raised - not possible with normal stairs, preserve facing and use STRAIGHT
            return new StairsConfig(currentFacing, Stairs.Shape.STRAIGHT);
        }

        // Fallback (should never reach here)
        return new StairsConfig(currentFacing, Stairs.Shape.STRAIGHT);
    }

    /**
     * Detect which octant (eighth of the block) was clicked.
     * Uses the clicked face and player position to disambiguate boundary cases.
     */
    private static Octant detectOctant(Block block, org.bukkit.util.Vector interactionPoint, BlockFace clickedFace, org.bukkit.util.Vector playerPos) {
        double x = interactionPoint.getX() - block.getX();
        double y = interactionPoint.getY() - block.getY();
        double z = interactionPoint.getZ() - block.getZ();

        boolean isEast = x >= 0.5;
        boolean isTop = y >= 0.5;
        boolean isSouth = z >= 0.5;

        // Boundary threshold - if click position is near the center, apply "reach through" logic
        double boundaryThreshold = 0.2; // Within 0.2 of center (0.3-0.7 range)

        // When clicking side faces near boundaries, flip to reach through
        // Otherwise, force to match the clicked face
        if (clickedFace == BlockFace.WEST) {
            if (Math.abs(x - 0.5) < boundaryThreshold) {
                isEast = true; // Near boundary, reaching for east corners
            } else {
                isEast = false; // Far from boundary, definitely west
            }
        } else if (clickedFace == BlockFace.EAST) {
            if (Math.abs(x - 0.5) < boundaryThreshold) {
                isEast = false; // Near boundary, reaching for west corners
            } else {
                isEast = true; // Far from boundary, definitely east
            }
        } else if (clickedFace == BlockFace.NORTH) {
            if (Math.abs(z - 0.5) < boundaryThreshold) {
                isSouth = true; // Near boundary, reaching for south corners
            } else {
                isSouth = false; // Far from boundary, definitely north
            }
        } else if (clickedFace == BlockFace.SOUTH) {
            if (Math.abs(z - 0.5) < boundaryThreshold) {
                isSouth = false; // Near boundary, reaching for north corners
            } else {
                isSouth = true; // Far from boundary, definitely south
            }
        }
        // For TOP/BOTTOM faces, use the position as-is

        Corner corner;
        if (isSouth && !isEast) {
            corner = Corner.SOUTH_WEST;
        } else if (!isSouth && !isEast) {
            corner = Corner.NORTH_WEST;
        } else if (!isSouth && isEast) {
            corner = Corner.NORTH_EAST;
        } else {
            corner = Corner.SOUTH_EAST;
        }

        return new Octant(corner, isTop);
    }

    /**
     * Rotate a directional block to the next facing.
     *
     * @param block the block to rotate
     * @return true if successful
     */
    public static boolean rotateDirectional(Block block) {
        if (!(block.getBlockData() instanceof Directional directional)) {
            return false;
        }

        BlockFace current = directional.getFacing();
        BlockFace next = getNextFacing(current, directional.getFaces());

        // Only modify if the facing actually changes
        if (current == next) {
            return false;
        }

        directional.setFacing(next);
        block.setBlockData(directional, false);
        return true;
    }

    /**
     * Rotate an axis block to the next axis.
     *
     * @param block the block to rotate
     * @return true if successful
     */
    public static boolean rotateAxis(Block block) {
        if (!(block.getBlockData() instanceof Orientable orientable)) {
            return false;
        }

        Axis current = orientable.getAxis();
        Axis next = switch (current) {
            case X -> Axis.Y;
            case Y -> Axis.Z;
            case Z -> Axis.X;
        };

        // Only modify if the axis actually changes
        if (current == next) {
            return false;
        }

        orientable.setAxis(next);
        block.setBlockData(orientable, false);
        return true;
    }

    /**
     * Toggle a slab between top and bottom (never double).
     *
     * @param block the block to toggle
     * @return true if successful
     */
    public static boolean toggleSlab(Block block) {
        if (!(block.getBlockData() instanceof Slab slab)) {
            return false;
        }

        // Only toggle between TOP and BOTTOM (never DOUBLE to prevent duplication)
        if (slab.getType() == Slab.Type.DOUBLE) {
            return false;
        }

        Slab.Type currentType = slab.getType();
        Slab.Type newType = currentType == Slab.Type.TOP ? Slab.Type.BOTTOM : Slab.Type.TOP;

        // Only modify if the type actually changes
        if (currentType == newType) {
            return false;
        }

        slab.setType(newType);
        block.setBlockData(slab, false);
        return true;
    }

    /**
     * Toggle stairs half between top and bottom.
     *
     * @param block the block to toggle
     * @return true if successful
     */
    public static boolean toggleStairsHalf(Block block) {
        if (!(block.getBlockData() instanceof Stairs stairs)) {
            return false;
        }

        Stairs.Half currentHalf = stairs.getHalf();
        Stairs.Half newHalf = currentHalf == Stairs.Half.TOP ? Stairs.Half.BOTTOM : Stairs.Half.TOP;

        // Only modify if the half actually changes
        if (currentHalf == newHalf) {
            return false;
        }

        stairs.setHalf(newHalf);
        block.setBlockData(stairs, false);
        return true;
    }

    /**
     * Check if a block is a flower pot (any variant).
     * Flower pots should use vanilla interaction (removing flowers).
     *
     * @param block the block to check
     * @return true if the block is a flower pot
     */
    public static boolean isFlowerPot(Block block) {
        String materialName = block.getType().name();
        return materialName.equals("FLOWER_POT") || materialName.startsWith("POTTED_");
    }

    /**
     * Check if a player can use tools in their current gamemode.
     *
     * @param player the player to check
     * @param config the plugin configuration
     * @return true if the player can use tools
     */
    public static boolean canUseInGamemode(Player player, FileConfiguration config) {
        GameMode mode = player.getGameMode();
        return switch (mode) {
            case CREATIVE -> config.getBoolean("general.restrictions.gamemode.creative.allow_use", true);
            case ADVENTURE -> config.getBoolean("general.restrictions.gamemode.adventure.allow_use", false);
            case SPECTATOR -> config.getBoolean("general.restrictions.gamemode.spectator.allow_use", false);
            default -> true;
        };
    }

    /**
     * Check if a block is an interactive block that should not be modified by tools.
     * Includes blocks with GUIs, redstone components, and special blocks.
     *
     * @param block the block to check
     * @return true if the block is interactive
     */
    public static boolean isInteractiveBlock(Block block) {
        return switch (block.getType()) {
            case CRAFTING_TABLE,
                 STONECUTTER,
                 LOOM,
                 GRINDSTONE,
                 CARTOGRAPHY_TABLE,
                 SMITHING_TABLE,
                 ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 ENCHANTING_TABLE,
                 ENDER_CHEST,
                 // Beds (right-click to sleep)
                 WHITE_BED, ORANGE_BED, MAGENTA_BED, LIGHT_BLUE_BED, YELLOW_BED,
                 LIME_BED, PINK_BED, GRAY_BED, LIGHT_GRAY_BED, CYAN_BED,
                 PURPLE_BED, BLUE_BED, BROWN_BED, GREEN_BED, RED_BED, BLACK_BED,
                 // Redstone components
                 LEVER,
                 REPEATER,
                 COMPARATOR,
                 // Buttons (all types)
                 OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON, JUNGLE_BUTTON,
                 ACACIA_BUTTON, DARK_OAK_BUTTON, MANGROVE_BUTTON, CHERRY_BUTTON,
                 BAMBOO_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON,
                 STONE_BUTTON, POLISHED_BLACKSTONE_BUTTON,
                 // Note blocks
                 NOTE_BLOCK,
                 // Dragon egg
                 DRAGON_EGG,
                 // Respawn anchor
                 RESPAWN_ANCHOR,
                 // Bell
                 BELL,
                 // Cake
                 CAKE -> true;
            default -> false;
        };
    }

    /**
     * Check if a player's current world is allowed for tool usage.
     *
     * @param player the player to check
     * @param config the plugin configuration
     * @return true if the player's world is allowed
     */
    public static boolean isWorldAllowed(Player player, FileConfiguration config) {
        String worldName = player.getWorld().getName();
        String mode = config.getString("general.restrictions.worlds.mode", "WHITELIST");
        java.util.List<String> worldList = config.getStringList("general.restrictions.worlds.list");

        if (mode.equalsIgnoreCase("WHITELIST")) {
            return worldList.contains(worldName);
        } else {
            return !worldList.contains(worldName);
        }
    }

    /**
     * Get the next facing from a set of valid faces.
     *
     * @param current the current facing
     * @param validFaces the valid faces
     * @return the next facing
     */
    private static BlockFace getNextFacing(BlockFace current, java.util.Set<BlockFace> validFaces) {
        BlockFace[] order = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

        int currentIndex = -1;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == current) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            return validFaces.iterator().next();
        }

        for (int i = 1; i <= order.length; i++) {
            int nextIndex = (currentIndex + i) % order.length;
            BlockFace next = order[nextIndex];
            if (validFaces.contains(next)) {
                return next;
            }
        }

        return current;
    }
}
