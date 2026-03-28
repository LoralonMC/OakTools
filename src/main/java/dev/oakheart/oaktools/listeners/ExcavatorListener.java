package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.managers.BreakingAnimationManager;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.DropHandler;
import dev.oakheart.oaktools.util.ExcavationCalculator;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.Collection;
import java.util.List;

/**
 * Handles the Excavation Shovel's 3x3 area mining on BlockBreakEvent.
 * When a player breaks a shovel-mineable block while holding the tool (and not sneaking),
 * the surrounding 3x3 blocks on the same face are queued for animated breaking.
 */
public class ExcavatorListener implements Listener {

    private final OakTools plugin;
    private final BreakingAnimationManager breakingManager;

    public ExcavatorListener(OakTools plugin, BreakingAnimationManager breakingManager) {
        this.plugin = plugin;
        this.breakingManager = breakingManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if holding excavator tool
        if (!plugin.getItemFactory().isTool(item)) return;
        if (plugin.getItemFactory().getToolType(item) != ToolType.EXCAVATOR) return;

        // Sneak to disable multi-block
        if (player.isSneaking()) return;

        // Guard checks
        if (!plugin.getConfigManager().isExcavatorEnabled()) return;
        if (!player.hasPermission("oaktools.use.excavator")) return;
        if (!plugin.getConfigManager().isGamemodeAllowed(player.getGameMode())) return;
        if (!plugin.getConfigManager().isWorldAllowed(player.getWorld().getName())) return;

        // Don't start a new operation if one is already active
        if (breakingManager.hasActiveOperation(player.getUniqueId())) return;

        // Check broken block is shovel-mineable
        Block brokenBlock = event.getBlock();
        if (!Tag.MINEABLE_SHOVEL.isTagged(brokenBlock.getType())) return;

        // Suppress vanilla drops — we handle all drops via inventory
        event.setDropItems(false);

        // Handle the initial block's drops ourselves (direct to inventory)
        ItemStack fakeTool = DropHandler.getFakeTool(ToolType.EXCAVATOR);
        Collection<ItemStack> initialDrops = brokenBlock.getDrops(fakeTool, player);
        DropHandler.Result initialResult = DropHandler.distributeDrops(player, initialDrops);

        // Ray trace to find which face was hit (for 3x3 grid orientation)
        RayTraceResult rayTrace = player.rayTraceBlocks(5.0);
        if (rayTrace == null || rayTrace.getHitBlock() == null || rayTrace.getHitBlockFace() == null) return;

        BlockFace face = rayTrace.getHitBlockFace();

        // Calculate 3x3 grid
        int maxBlocks = plugin.getConfigManager().getMaxBlocks(ToolType.EXCAVATOR);
        List<Block> blocks = ExcavationCalculator.calculate(brokenBlock, face, maxBlocks);

        // Remove the already-broken block from the list (vanilla handles the break itself)
        blocks.removeIf(b -> b.equals(brokenBlock));

        // Filter by protection
        blocks.removeIf(b -> !plugin.getProtectionService().canBreakBlock(player, b));

        if (blocks.isEmpty()) {
            // No surrounding blocks — handle any initial overflow
            if (!initialResult.overflowItems().isEmpty()) {
                DropHandler.flushOverflow(player, initialResult.overflowItems(), plugin.getOverflowHook());
            }
            return;
        }

        // Start breaking operation (pass initial overflow so it gets merged with the rest)
        breakingManager.startOperation(player, ToolType.EXCAVATOR, blocks,
                "excavator-breaking", "excavator-complete", 1, initialResult.overflowItems());
    }
}
