package dev.oakheart.oaktools.model;

/**
 * Represents the type of block state edit performed by the File tool.
 */
public enum EditType {
    MULTIPLE_FACING,  // Fences, glass panes, iron bars
    WALL,             // Wall heights and up flag
    STAIRS,           // Stairs shape, half, facing
    DIRECTIONAL,      // Furnaces, hoppers, dispensers, etc.
    AXIS,             // Logs, pillars, bone blocks
    SLAB              // Top/bottom only (no double)
}
