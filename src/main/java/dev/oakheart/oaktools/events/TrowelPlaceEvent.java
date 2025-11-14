package dev.oakheart.oaktools.events;

import dev.oakheart.oaktools.model.FeedSource;
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

/**
 * Called when a Trowel places a block.
 * Fires before BlockPlaceEvent, allowing pre-validation.
 * Cancelling this event prevents placement and durability consumption.
 */
public class TrowelPlaceEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Block block;
    private final BlockData placedData;
    private final Material consumedMaterial;
    private final ItemStack tool;
    private final ItemStack chosenStack;
    private final FeedSource feedSource;
    private final EquipmentSlot hand;
    private final BlockFace clickedFace;

    public TrowelPlaceEvent(@NotNull Player player, Block block, BlockData placedData,
                            Material consumedMaterial, ItemStack tool, ItemStack chosenStack,
                            FeedSource feedSource, EquipmentSlot hand, BlockFace clickedFace) {
        super(player);
        this.block = block;
        this.placedData = placedData;
        this.consumedMaterial = consumedMaterial;
        this.tool = tool;
        this.chosenStack = chosenStack.clone(); // Clone for safety
        this.feedSource = feedSource;
        this.hand = hand;
        this.clickedFace = clickedFace;
    }

    /**
     * Get the block being placed at.
     *
     * @return the block
     */
    public Block getBlock() {
        return block;
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
     * Get the material being consumed from inventory.
     *
     * @return the consumed material
     */
    public Material getConsumedMaterial() {
        return consumedMaterial;
    }

    /**
     * Get the Trowel tool being used.
     *
     * @return the tool item
     */
    public ItemStack getTool() {
        return tool;
    }

    /**
     * Get a snapshot of the chosen stack from inventory.
     * This is a clone for auditing purposes - mutations won't affect the original.
     *
     * @return the chosen stack (clone)
     */
    public ItemStack getChosenStack() {
        return chosenStack;
    }

    /**
     * Get the feed source being used.
     *
     * @return the feed source
     */
    public FeedSource getFeedSource() {
        return feedSource;
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
