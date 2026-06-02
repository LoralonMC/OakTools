package dev.oakheart.oaktools.util;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;

import java.util.logging.Logger;

public class BlockUtil {

    public static boolean hasMultipleFacing(Block block) {
        return block.getBlockData() instanceof MultipleFacing;
    }

    public static boolean isWall(Block block) {
        return block.getBlockData() instanceof Wall;
    }

    public static boolean isStairs(Block block) {
        return block.getBlockData() instanceof Stairs;
    }

    public static boolean isWaterloggable(Block block) {
        return block.getBlockData() instanceof Waterlogged;
    }

    public static boolean isDirectional(Block block) {
        return block.getBlockData() instanceof Directional;
    }

    /**
     * Whether this block is a piston that should not be rotated by the File tool.
     * Rotating an <em>extended</em> piston base desyncs it from its head, which can
     * leave a floating head and enable block-duplication glitches. Retracted pistons
     * are safe to rotate and are not protected.
     *
     * @return true for an extended PISTON/STICKY_PISTON base, a PISTON_HEAD, or a
     *         MOVING_PISTON; false otherwise (including retracted piston bases)
     */
    public static boolean isProtectedPiston(Block block) {
        Material type = block.getType();
        if (type == Material.PISTON_HEAD || type == Material.MOVING_PISTON) {
            return true;
        }
        return block.getBlockData() instanceof org.bukkit.block.data.type.Piston piston && piston.isExtended();
    }

    public static boolean hasAxis(Block block) {
        return block.getBlockData() instanceof Orientable;
    }

    public static boolean isSlab(Block block) {
        return block.getBlockData() instanceof Slab;
    }

    public static boolean cycleMultipleFacing(Block block, BlockFace clickedFace, org.bukkit.util.Vector interactionPoint, BlockFace playerFacing) {
        if (!(block.getBlockData() instanceof MultipleFacing multipleFacing)) {
            return false;
        }

        var allowedFaces = multipleFacing.getAllowedFaces();
        if (allowedFaces.isEmpty()) {
            return false;
        }

        BlockFace faceToToggle;
        if (interactionPoint != null) {
            faceToToggle = getClosestHorizontalFace(block, interactionPoint);
        } else {
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

    private static BlockFace getClosestHorizontalFace(Block block, org.bukkit.util.Vector interactionPoint) {
        double x = interactionPoint.getX() - block.getX();
        double z = interactionPoint.getZ() - block.getZ();

        double relX = x - 0.5;
        double relZ = z - 0.5;

        if (Math.abs(relX) > Math.abs(relZ)) {
            return relX > 0 ? BlockFace.EAST : BlockFace.WEST;
        } else {
            return relZ > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
    }

    public static boolean cycleWall(Block block, BlockFace clickedFace, org.bukkit.util.Vector interactionPoint, BlockFace playerFacing) {
        if (!(block.getBlockData() instanceof Wall wall)) {
            return false;
        }

        BlockFace sideToModify;
        if (interactionPoint != null) {
            sideToModify = getClosestHorizontalFace(block, interactionPoint);
        } else {
            sideToModify = switch (clickedFace) {
                case NORTH, SOUTH, EAST, WEST -> clickedFace;
                case UP, DOWN -> playerFacing;
                default -> playerFacing;
            };
        }

        if (sideToModify != BlockFace.NORTH && sideToModify != BlockFace.SOUTH &&
            sideToModify != BlockFace.EAST && sideToModify != BlockFace.WEST) {
            return false;
        }

        Wall.Height currentHeight = wall.getHeight(sideToModify);
        Wall.Height nextHeight = switch (currentHeight) {
            case NONE -> Wall.Height.LOW;
            case LOW -> Wall.Height.TALL;
            case TALL -> Wall.Height.NONE;
        };

        if (currentHeight == nextHeight) {
            return false;
        }

        wall.setHeight(sideToModify, nextHeight);
        wall.setUp(shouldWallBeUp(wall));
        block.setBlockData(wall, false);
        return true;
    }

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

    public static boolean editStairsShape(Block block, BlockFace clickedFace, org.bukkit.util.Vector interactionPoint, org.bukkit.util.Vector playerPos, Logger debugLogger) {
        if (!(block.getBlockData() instanceof Stairs stairs)) {
            return false;
        }

        if (interactionPoint != null) {
            StairsConfig config = determineStairsConfig(block, interactionPoint, clickedFace, playerPos, debugLogger);

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

    private static class StairsConfig {
        BlockFace facing;
        Stairs.Shape shape;

        StairsConfig(BlockFace facing, Stairs.Shape shape) {
            this.facing = facing;
            this.shape = shape;
        }
    }

    private enum Corner {
        SOUTH_WEST, NORTH_WEST, NORTH_EAST, SOUTH_EAST
    }

    private static class Octant {
        Corner corner;
        boolean isTop;

        Octant(Corner corner, boolean isTop) {
            this.corner = corner;
            this.isTop = isTop;
        }
    }

    private static StairsConfig determineStairsConfig(Block block, org.bukkit.util.Vector interactionPoint, BlockFace clickedFace, org.bukkit.util.Vector playerPos, Logger debugLogger) {
        if (!(block.getBlockData() instanceof Stairs stairs)) {
            return new StairsConfig(BlockFace.NORTH, Stairs.Shape.STRAIGHT);
        }

        BlockFace currentFacing = stairs.getFacing();

        Octant octant = detectOctant(block, interactionPoint, clickedFace, playerPos);
        Corner clickedCorner = octant.corner;

        if (debugLogger != null) {
            debugLogger.info(String.format(
                "[File Debug] Stairs click - Corner: %s, IsTop: %s, Face: %s, Current: %s %s, Click: %.2f,%.2f,%.2f, Player: %.2f,%.2f",
                clickedCorner, octant.isTop, clickedFace, stairs.getFacing(), stairs.getShape(),
                interactionPoint.getX() % 1, interactionPoint.getY() % 1, interactionPoint.getZ() % 1,
                playerPos.getX(), playerPos.getZ()
            ));
        }

        boolean[] raisedCorners = getRaisedCorners(stairs.getFacing(), stairs.getShape());

        if (debugLogger != null) {
            debugLogger.info(String.format(
                "[File Debug] Current raised: SW=%s NW=%s NE=%s SE=%s",
                raisedCorners[0], raisedCorners[1], raisedCorners[2], raisedCorners[3]
            ));
        }

        int cornerIndex = getCornerIndex(clickedCorner);
        raisedCorners[cornerIndex] = !raisedCorners[cornerIndex];

        if (debugLogger != null) {
            debugLogger.info(String.format(
                "[File Debug] New raised: SW=%s NW=%s NE=%s SE=%s",
                raisedCorners[0], raisedCorners[1], raisedCorners[2], raisedCorners[3]
            ));
        }

        StairsConfig result = calculateStairsFromCorners(raisedCorners, currentFacing);

        if (debugLogger != null) {
            debugLogger.info(String.format(
                "[File Debug] Result: %s %s", result.facing, result.shape
            ));
        }

        return result;
    }

    private static boolean[] getRaisedCorners(BlockFace facing, Stairs.Shape shape) {
        boolean[] raised = new boolean[4]; // SW, NW, NE, SE

        switch (shape) {
            case STRAIGHT -> {
                raised[1] = true;
                raised[2] = true;
            }
            case INNER_LEFT -> {
                raised[0] = true;
                raised[1] = true;
                raised[2] = true;
            }
            case INNER_RIGHT -> {
                raised[1] = true;
                raised[2] = true;
                raised[3] = true;
            }
            case OUTER_LEFT -> {
                raised[1] = true;
            }
            case OUTER_RIGHT -> {
                raised[2] = true;
            }
        }

        return rotateRaisedCorners(raised, facing);
    }

    private static boolean[] rotateRaisedCorners(boolean[] corners, BlockFace facing) {
        int rotations = switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };

        boolean[] result = corners.clone();
        for (int i = 0; i < rotations; i++) {
            result = rotateRaisedCornersClockwise(result);
        }
        return result;
    }

    private static boolean[] rotateRaisedCornersClockwise(boolean[] corners) {
        return new boolean[] {
            corners[3],
            corners[0],
            corners[1],
            corners[2]
        };
    }

    private static int getCornerIndex(Corner corner) {
        return switch (corner) {
            case SOUTH_WEST -> 0;
            case NORTH_WEST -> 1;
            case NORTH_EAST -> 2;
            case SOUTH_EAST -> 3;
        };
    }

    private static StairsConfig calculateStairsFromCorners(boolean[] raised, BlockFace currentFacing) {
        int count = 0;
        for (boolean r : raised) {
            if (r) count++;
        }

        if (count == 0) {
            return new StairsConfig(currentFacing, Stairs.Shape.STRAIGHT);
        }

        if (count == 1) {
            if (raised[0]) return new StairsConfig(BlockFace.WEST, Stairs.Shape.OUTER_LEFT);
            if (raised[1]) return new StairsConfig(BlockFace.NORTH, Stairs.Shape.OUTER_LEFT);
            if (raised[2]) return new StairsConfig(BlockFace.EAST, Stairs.Shape.OUTER_LEFT);
            if (raised[3]) return new StairsConfig(BlockFace.SOUTH, Stairs.Shape.OUTER_LEFT);
        }

        if (count == 2) {
            if (raised[0] && raised[3]) return new StairsConfig(BlockFace.SOUTH, Stairs.Shape.STRAIGHT);
            if (raised[1] && raised[2]) return new StairsConfig(BlockFace.NORTH, Stairs.Shape.STRAIGHT);
            if (raised[0] && raised[1]) return new StairsConfig(BlockFace.WEST, Stairs.Shape.STRAIGHT);
            if (raised[2] && raised[3]) return new StairsConfig(BlockFace.EAST, Stairs.Shape.STRAIGHT);

            if (raised[0] && raised[2]) return new StairsConfig(BlockFace.NORTH, Stairs.Shape.STRAIGHT);
            if (raised[1] && raised[3]) return new StairsConfig(BlockFace.NORTH, Stairs.Shape.STRAIGHT);
        }

        if (count == 3) {
            if (!raised[0]) return new StairsConfig(BlockFace.NORTH, Stairs.Shape.INNER_RIGHT);
            if (!raised[1]) return new StairsConfig(BlockFace.EAST, Stairs.Shape.INNER_RIGHT);
            if (!raised[2]) return new StairsConfig(BlockFace.SOUTH, Stairs.Shape.INNER_RIGHT);
            if (!raised[3]) return new StairsConfig(BlockFace.WEST, Stairs.Shape.INNER_RIGHT);
        }

        if (count == 4) {
            return new StairsConfig(currentFacing, Stairs.Shape.STRAIGHT);
        }

        return new StairsConfig(currentFacing, Stairs.Shape.STRAIGHT);
    }

    private static Octant detectOctant(Block block, org.bukkit.util.Vector interactionPoint, BlockFace clickedFace, org.bukkit.util.Vector playerPos) {
        double x = interactionPoint.getX() - block.getX();
        double y = interactionPoint.getY() - block.getY();
        double z = interactionPoint.getZ() - block.getZ();

        boolean isEast = x >= 0.5;
        boolean isTop = y >= 0.5;
        boolean isSouth = z >= 0.5;

        double boundaryThreshold = 0.2;

        if (clickedFace == BlockFace.WEST) {
            if (Math.abs(x - 0.5) < boundaryThreshold) {
                isEast = true;
            } else {
                isEast = false;
            }
        } else if (clickedFace == BlockFace.EAST) {
            if (Math.abs(x - 0.5) < boundaryThreshold) {
                isEast = false;
            } else {
                isEast = true;
            }
        } else if (clickedFace == BlockFace.NORTH) {
            if (Math.abs(z - 0.5) < boundaryThreshold) {
                isSouth = true;
            } else {
                isSouth = false;
            }
        } else if (clickedFace == BlockFace.SOUTH) {
            if (Math.abs(z - 0.5) < boundaryThreshold) {
                isSouth = false;
            } else {
                isSouth = true;
            }
        }

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

    public static boolean rotateDirectional(Block block) {
        if (!(block.getBlockData() instanceof Directional directional)) {
            return false;
        }

        BlockFace current = directional.getFacing();
        BlockFace next = getNextFacing(current, directional.getFaces());

        if (current == next) {
            return false;
        }

        directional.setFacing(next);
        block.setBlockData(directional, false);
        return true;
    }

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

        if (current == next) {
            return false;
        }

        orientable.setAxis(next);
        block.setBlockData(orientable, false);
        return true;
    }

    public static boolean toggleSlab(Block block) {
        if (!(block.getBlockData() instanceof Slab slab)) {
            return false;
        }

        if (slab.getType() == Slab.Type.DOUBLE) {
            return false;
        }

        Slab.Type currentType = slab.getType();
        Slab.Type newType = currentType == Slab.Type.TOP ? Slab.Type.BOTTOM : Slab.Type.TOP;

        if (currentType == newType) {
            return false;
        }

        slab.setType(newType);
        block.setBlockData(slab, false);
        return true;
    }

    public static boolean toggleStairsHalf(Block block) {
        if (!(block.getBlockData() instanceof Stairs stairs)) {
            return false;
        }

        Stairs.Half currentHalf = stairs.getHalf();
        Stairs.Half newHalf = currentHalf == Stairs.Half.TOP ? Stairs.Half.BOTTOM : Stairs.Half.TOP;

        if (currentHalf == newHalf) {
            return false;
        }

        stairs.setHalf(newHalf);
        block.setBlockData(stairs, false);
        return true;
    }

    public static boolean isFlowerPot(Block block) {
        String materialName = block.getType().name();
        return materialName.equals("FLOWER_POT") || materialName.startsWith("POTTED_");
    }

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
                 WHITE_BED, ORANGE_BED, MAGENTA_BED, LIGHT_BLUE_BED, YELLOW_BED,
                 LIME_BED, PINK_BED, GRAY_BED, LIGHT_GRAY_BED, CYAN_BED,
                 PURPLE_BED, BLUE_BED, BROWN_BED, GREEN_BED, RED_BED, BLACK_BED,
                 LEVER,
                 REPEATER,
                 COMPARATOR,
                 OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON, JUNGLE_BUTTON,
                 ACACIA_BUTTON, DARK_OAK_BUTTON, MANGROVE_BUTTON, CHERRY_BUTTON,
                 BAMBOO_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON,
                 STONE_BUTTON, POLISHED_BLACKSTONE_BUTTON,
                 NOTE_BLOCK,
                 DRAGON_EGG,
                 RESPAWN_ANCHOR,
                 BELL,
                 CAKE -> true;
            default -> false;
        };
    }

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
