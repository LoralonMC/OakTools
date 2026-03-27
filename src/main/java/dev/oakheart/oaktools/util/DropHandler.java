package dev.oakheart.oaktools.util;

import dev.oakheart.oaktools.integration.OverflowHook;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashMap;

/**
 * Handles block drop calculation and distribution for harvesting tools.
 * Uses fake vanilla tools for proper drop calculation, since the actual
 * tool items use custom base materials (e.g. WARPED_FUNGUS_ON_A_STICK).
 */
public class DropHandler {

    private static final ItemStack FAKE_SHOVEL = new ItemStack(Material.DIAMOND_SHOVEL);
    private static final ItemStack FAKE_AXE = new ItemStack(Material.DIAMOND_AXE);
    private static final ItemStack FAKE_PICKAXE = new ItemStack(Material.DIAMOND_PICKAXE);

    /**
     * Calculates drops using the appropriate vanilla tool, breaks the block,
     * grants XP, and distributes items to the player's inventory.
     * Overflow goes to OakOverflow if available, otherwise drops on ground.
     *
     * @return the number of individual items collected
     */
    public static int handleBlockBreak(Player player, Block block, ToolType toolType, OverflowHook overflowHook) {
        ItemStack fakeTool = getFakeTool(toolType);

        // Calculate drops before breaking the block
        Collection<ItemStack> drops = block.getDrops(fakeTool, player);

        // Break the block
        block.setType(Material.AIR);

        // Distribute drops
        int itemCount = 0;
        for (ItemStack drop : drops) {
            itemCount += drop.getAmount();
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(drop);

            for (ItemStack leftover : overflow.values()) {
                if (overflowHook != null && overflowHook.isAvailable()) {
                    overflowHook.sendToOverflow(player, leftover, leftover.getAmount());
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }

        return itemCount;
    }

    private static ItemStack getFakeTool(ToolType toolType) {
        return switch (toolType) {
            case EXCAVATOR -> FAKE_SHOVEL;
            case LUMBERJACK -> FAKE_AXE;
            case VEIN_MINER -> FAKE_PICKAXE;
            default -> FAKE_PICKAXE;
        };
    }
}
