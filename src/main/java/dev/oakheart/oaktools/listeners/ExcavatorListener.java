package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.managers.BreakingAnimationManager;
import dev.oakheart.oaktools.managers.HarvestPreviewManager;
import dev.oakheart.oaktools.model.ToolType;
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

import java.util.List;

/**
 * Handles the Excavation Shovel's 3x3 area mining on BlockBreakEvent.
 * When a player breaks a shovel-mineable block while holding the tool (and not sneaking),
 * the surrounding 3x3 blocks on the same face are queued for animated breaking.
 */
public class ExcavatorListener implements Listener {

    private final OakTools plugin;
    private final BreakingAnimationManager breakingManager;
    private final HarvestPreviewManager previewManager;

    public ExcavatorListener(OakTools plugin, BreakingAnimationManager breakingManager,
                             HarvestPreviewManager previewManager) {
        this.plugin = plugin;
        this.breakingManager = breakingManager;
        this.previewManager = previewManager;
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

        // Ray trace to find which face was hit (for 3x3 grid orientation)
        RayTraceResult rayTrace = player.rayTraceBlocks(5.0);
        if (rayTrace == null || rayTrace.getHitBlock() == null || rayTrace.getHitBlockFace() == null) return;

        BlockFace face = rayTrace.getHitBlockFace();

        // Calculate 3x3 grid
        int maxBlocks = plugin.getConfigManager().getMaxBlocks(ToolType.EXCAVATOR);
        List<Block> blocks = ExcavationCalculator.calculate(brokenBlock, face, maxBlocks);

        // Remove the already-broken block from the list (vanilla handles it)
        blocks.removeIf(b -> b.equals(brokenBlock));

        // Filter by protection
        blocks.removeIf(b -> !plugin.getProtectionService().canBreakBlock(player, b));

        if (blocks.isEmpty()) return;

        // Clear preview and start breaking operation
        if (previewManager != null) {
            previewManager.clearPreviewForPlayer(player);
        }

        breakingManager.startOperation(player, ToolType.EXCAVATOR, blocks,
                "excavator-breaking", "excavator-complete");
    }
}
