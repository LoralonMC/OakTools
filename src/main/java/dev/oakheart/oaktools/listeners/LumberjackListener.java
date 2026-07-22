package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.managers.BreakingAnimationManager;
import dev.oakheart.oaktools.managers.PlacedBlockTracker;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.DropHandler;
import dev.oakheart.oaktools.util.TreeDetector;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * Handles the Lumberjack's Axe tree-felling on BlockBreakEvent.
 * When a player breaks a natural log while holding the tool (and not sneaking),
 * the entire connected tree is queued for animated breaking.
 */
public class LumberjackListener implements Listener {

    private final OakTools plugin;
    private final BreakingAnimationManager breakingManager;
    private final PlacedBlockTracker placedBlockTracker;

    public LumberjackListener(OakTools plugin, BreakingAnimationManager breakingManager,
                              PlacedBlockTracker placedBlockTracker) {
        this.plugin = plugin;
        this.breakingManager = breakingManager;
        this.placedBlockTracker = placedBlockTracker;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Protection probes fire fake BlockBreakEvents; reacting to one recurses.
        if (plugin.getProtectionService().isFiringProbe()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if holding lumberjack tool
        if (!plugin.getItemFactory().isTool(item)) return;
        if (plugin.getItemFactory().getToolType(item) != ToolType.LUMBERJACK) return;

        // Sneak to disable multi-block
        if (player.isSneaking()) return;

        // Guard checks
        if (!plugin.getConfigManager().isLumberjackEnabled()) return;
        if (!player.hasPermission("oaktools.use.lumberjack")) return;
        if (!plugin.getConfigManager().isGamemodeAllowed(player.getGameMode())) return;
        if (!plugin.getConfigManager().isWorldAllowed(player.getWorld().getName())) return;

        // Don't start a new operation if one is already active
        if (breakingManager.hasActiveOperation(player.getUniqueId())) return;

        // Check broken block is a natural log
        Block brokenBlock = event.getBlock();
        if (!TreeDetector.isNaturalLog(brokenBlock.getType())) return;

        // Detect tree (returns empty if not a valid generated tree)
        int maxBlocks = plugin.getConfigManager().getMaxBlocks(ToolType.LUMBERJACK);
        int minLeaves = plugin.getConfigManager().getLumberjackMinLeaves();
        List<Block> logs = TreeDetector.detectTree(brokenBlock, maxBlocks, minLeaves, placedBlockTracker);

        if (logs.isEmpty()) {
            // Not a natural tree — send message and let vanilla handle the single block
            plugin.getMessageManager().send(player, "not-a-tree");
            return;
        }

        // Filter by protection
        logs.removeIf(b -> !plugin.getProtectionService().canBreakBlock(player, b));

        if (logs.isEmpty()) return;

        // Suppress vanilla drops — we handle all drops via inventory
        event.setDropItems(false);

        // Handle the initial block's drops ourselves (direct to inventory)
        ItemStack fakeTool = DropHandler.getFakeTool(ToolType.LUMBERJACK, item);
        Collection<ItemStack> initialDrops = brokenBlock.getDrops(fakeTool, player);
        DropHandler.Result initialResult = DropHandler.distributeDrops(player, initialDrops);

        // Start breaking operation
        breakingManager.startOperation(player, ToolType.LUMBERJACK, logs,
                "lumberjack-breaking", "lumberjack-complete", 1, initialResult.overflowItems());
    }
}
