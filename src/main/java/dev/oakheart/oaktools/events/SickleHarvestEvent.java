package dev.oakheart.oaktools.events;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called once per crop harvested by the Sickle, including the radius crops that
 * are cleared directly (setType/replant) and therefore never fire a
 * BlockBreakEvent. Purely informational - fired after the harvest, not
 * cancellable - so downstream systems (e.g. quest objectives) can credit a
 * sickle sweep the same as a hand harvest.
 *
 * NOT fired for the initially clicked crop: that one already fires a normal
 * (pre-cancel) BlockBreakEvent, so re-announcing it here would double count.
 */
public class SickleHarvestEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block block;
    private final Material crop;

    public SickleHarvestEvent(@NotNull Player player, @NotNull Block block, @NotNull Material crop) {
        super(player);
        this.block = block;
        this.crop = crop;
    }

    /** The harvested crop block (already cleared/replanted by the time this fires). */
    public Block getBlock() {
        return block;
    }

    /** The crop material that was harvested (e.g. WHEAT). */
    public Material getCrop() {
        return crop;
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
