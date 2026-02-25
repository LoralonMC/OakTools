package dev.oakheart.oaktools.events;

import dev.oakheart.oaktools.model.WandMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Called when a Builder's Wand places blocks.
 * Fires before placement, allowing pre-validation.
 * Cancelling this event prevents placement and durability consumption.
 */
public class WandPlaceEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final List<Block> blocks;
    private final BlockData placedData;
    private final Material sourceMaterial;
    private final ItemStack tool;
    private final EquipmentSlot hand;
    private final BlockFace clickedFace;
    private final WandMode wandMode;

    public WandPlaceEvent(@NotNull Player player, List<Block> blocks, BlockData placedData,
                          Material sourceMaterial, ItemStack tool, EquipmentSlot hand,
                          BlockFace clickedFace, WandMode wandMode) {
        super(player);
        this.blocks = Collections.unmodifiableList(blocks);
        this.placedData = placedData;
        this.sourceMaterial = sourceMaterial;
        this.tool = tool;
        this.hand = hand;
        this.clickedFace = clickedFace;
        this.wandMode = wandMode;
    }

    /**
     * Get the blocks that will be placed (unmodifiable).
     *
     * @return the list of blocks
     */
    public List<Block> getBlocks() {
        return blocks;
    }

    /**
     * Get the block data being placed.
     *
     * @return the placed block data
     */
    public BlockData getPlacedData() {
        return placedData;
    }

    /**
     * Get the source material being placed.
     *
     * @return the source material
     */
    public Material getSourceMaterial() {
        return sourceMaterial;
    }

    /**
     * Get the Builder's Wand tool being used.
     *
     * @return the tool item
     */
    public ItemStack getTool() {
        return tool;
    }

    /**
     * Get the equipment slot (hand) used.
     *
     * @return the equipment slot
     */
    public EquipmentSlot getHand() {
        return hand;
    }

    /**
     * Get the face that was clicked.
     *
     * @return the clicked face
     */
    public BlockFace getClickedFace() {
        return clickedFace;
    }

    /**
     * Get the wand mode used for this placement.
     *
     * @return the wand mode
     */
    public WandMode getWandMode() {
        return wandMode;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
