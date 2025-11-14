package dev.oakheart.oaktools.events;

import dev.oakheart.oaktools.model.EditType;
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

/**
 * Called when a File tool modifies a block state.
 * Cancelling this event prevents the state change and durability consumption.
 */
public class FileUseEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Block block;
    private final BlockData oldData;
    private final BlockData newData;
    private final ItemStack tool;
    private final BlockFace clickedFace;
    private final EquipmentSlot hand;
    private final EditType editType;

    public FileUseEvent(@NotNull Player player, Block block, BlockData oldData, BlockData newData,
                        ItemStack tool, BlockFace clickedFace, EquipmentSlot hand, EditType editType) {
        super(player);
        this.block = block;
        this.oldData = oldData;
        this.newData = newData;
        this.tool = tool;
        this.clickedFace = clickedFace;
        this.hand = hand;
        this.editType = editType;
    }

    /**
     * Get the block being modified.
     *
     * @return the block
     */
    public Block getBlock() {
        return block;
    }

    /**
     * Get the old block data (before modification).
     *
     * @return the old block data
     */
    public BlockData getOldData() {
        return oldData;
    }

    /**
     * Get the new block data (after modification).
     *
     * @return the new block data
     */
    public BlockData getNewData() {
        return newData;
    }

    /**
     * Get the File tool being used.
     *
     * @return the tool item
     */
    public ItemStack getTool() {
        return tool;
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
     * Get the equipment slot (hand) used.
     *
     * @return the equipment slot
     */
    public EquipmentSlot getHand() {
        return hand;
    }

    /**
     * Get the type of edit being performed.
     *
     * @return the edit type
     */
    public EditType getEditType() {
        return editType;
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
