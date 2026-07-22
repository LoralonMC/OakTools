package dev.oakheart.oaktools.events;

import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called once per EXTRA block removed by a multi-block OakTools operation
 * (Lumberjack fell, Vein Miner, Excavator) - the cascade blocks that
 * BreakingAnimationManager clears directly and which therefore never fire a
 * BlockBreakEvent. Purely informational, not cancellable, fired right after the
 * block is broken, so downstream systems (e.g. quest objectives) can credit a
 * whole tree / vein / dig the same as breaking each block by hand.
 *
 * NOT fired for the initially clicked block: that one fires a normal
 * BlockBreakEvent already, so re-announcing it here would double count.
 */
public class OakToolBlockBreakEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block block;
    private final Material blockType;
    private final ToolType toolType;

    public OakToolBlockBreakEvent(@NotNull Player player, @NotNull Block block,
                                  @NotNull Material blockType, @NotNull ToolType toolType) {
        super(player);
        this.block = block;
        this.blockType = blockType;
        this.toolType = toolType;
    }

    /** The block that was broken (already cleared to air by the time this fires). */
    @NotNull
    public Block getBlock() {
        return block;
    }

    /** The material of the block that was broken (e.g. OAK_LOG). */
    @NotNull
    public Material getBlockType() {
        return blockType;
    }

    /** Which multi-block tool caused the break. */
    @NotNull
    public ToolType getToolType() {
        return toolType;
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
