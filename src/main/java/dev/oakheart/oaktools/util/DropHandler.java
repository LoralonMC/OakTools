package dev.oakheart.oaktools.util;

import dev.oakheart.oaktools.integration.OverflowHook;
import dev.oakheart.oaktools.model.ToolType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles block drop calculation and distribution for harvesting tools.
 * Uses fake vanilla tools for proper drop calculation, since the actual
 * tool items use custom base materials (e.g. NETHERITE_SHOVEL).
 */
public class DropHandler {

    private static final ItemStack FAKE_SHOVEL = new ItemStack(Material.DIAMOND_SHOVEL);
    private static final ItemStack FAKE_AXE = new ItemStack(Material.DIAMOND_AXE);
    private static final ItemStack FAKE_PICKAXE = new ItemStack(Material.DIAMOND_PICKAXE);

    /**
     * Calculates drops, breaks the block, and adds items to the player's inventory.
     * Items that don't fit are returned as overflow (caller handles batching/sending).
     */
    public static Result handleBlockBreak(Player player, Block block, ToolType toolType, ItemStack actualTool) {
        ItemStack fakeTool = getFakeTool(toolType, actualTool);

        // Calculate drops before breaking the block
        Collection<ItemStack> drops = block.getDrops(fakeTool, player);

        // Break the block
        block.setType(Material.AIR);

        return distributeDrops(player, drops);
    }

    /**
     * Adds pre-calculated drops to the player's inventory.
     * Used for the initial block where vanilla handles the break but we suppress drops.
     */
    public static Result distributeDrops(Player player, Collection<ItemStack> drops) {
        int itemCount = 0;
        List<ItemStack> overflow = new ArrayList<>();

        for (ItemStack drop : drops) {
            itemCount += drop.getAmount();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
            overflow.addAll(leftover.values());
        }

        return new Result(itemCount, overflow);
    }

    /**
     * Merges a list of overflow items by material type, then sends to OakOverflow.
     * Falls back to dropping on the ground if OakOverflow is not available.
     */
    public static void flushOverflow(Player player, List<ItemStack> overflowItems, OverflowHook overflowHook) {
        if (overflowItems.isEmpty()) return;

        // Merge stacks by material type
        Map<Material, Integer> merged = new HashMap<>();
        Map<Material, ItemStack> templates = new HashMap<>();
        for (ItemStack item : overflowItems) {
            merged.merge(item.getType(), item.getAmount(), Integer::sum);
            templates.putIfAbsent(item.getType(), item);
        }

        for (var entry : merged.entrySet()) {
            Material material = entry.getKey();
            int totalAmount = entry.getValue();
            ItemStack template = templates.get(material);

            if (overflowHook != null && overflowHook.isAvailable()) {
                ItemStack overflowItem = template.clone();
                overflowItem.setAmount(totalAmount);
                overflowHook.sendToOverflow(player, overflowItem, totalAmount);
            } else {
                // Drop in full stacks
                while (totalAmount > 0) {
                    ItemStack drop = template.clone();
                    int stackSize = Math.min(totalAmount, material.getMaxStackSize());
                    drop.setAmount(stackSize);
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    totalAmount -= stackSize;
                }
            }
        }
    }

    public static ItemStack getFakeTool(ToolType toolType) {
        return switch (toolType) {
            case EXCAVATOR -> FAKE_SHOVEL;
            case LUMBERJACK -> FAKE_AXE;
            case VEIN_MINER -> FAKE_PICKAXE;
            default -> FAKE_PICKAXE;
        };
    }

    /**
     * Fake tool carrying the real tool's enchantments, so drop-affecting
     * enchants (Fortune, Silk Touch — obtainable only when the tool's
     * allowed-enchantments permits them) apply to drop calculation.
     */
    public static ItemStack getFakeTool(ToolType toolType, ItemStack actualTool) {
        ItemStack fakeTool = getFakeTool(toolType);
        if (actualTool == null || actualTool.getEnchantments().isEmpty()) {
            return fakeTool;
        }
        ItemStack enchanted = fakeTool.clone();
        enchanted.addUnsafeEnchantments(actualTool.getEnchantments());
        return enchanted;
    }

    public record Result(int itemCount, List<ItemStack> overflowItems) {}
}
