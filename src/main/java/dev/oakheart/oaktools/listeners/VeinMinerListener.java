package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.managers.BreakingAnimationManager;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.DropHandler;
import dev.oakheart.oaktools.util.OreGrouping;
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
 * Handles the Vein Miner Pickaxe's connected ore mining on BlockBreakEvent.
 * When a player breaks an ore block while holding the tool (and not sneaking),
 * all connected ore blocks in the vein are queued for animated breaking.
 */
public class VeinMinerListener implements Listener {

    private final OakTools plugin;
    private final BreakingAnimationManager breakingManager;

    public VeinMinerListener(OakTools plugin, BreakingAnimationManager breakingManager) {
        this.plugin = plugin;
        this.breakingManager = breakingManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Protection probes fire fake BlockBreakEvents; reacting to one recurses.
        if (plugin.getProtectionService().isFiringProbe()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if holding vein miner tool
        if (!plugin.getItemFactory().isTool(item)) return;
        if (plugin.getItemFactory().getToolType(item) != ToolType.VEIN_MINER) return;

        // Sneak to disable multi-block
        if (player.isSneaking()) return;

        // Guard checks
        if (!plugin.getConfigManager().isVeinMinerEnabled()) return;
        if (!player.hasPermission("oaktools.use.veinminer")) return;
        if (!plugin.getConfigManager().isGamemodeAllowed(player.getGameMode())) return;
        if (!plugin.getConfigManager().isWorldAllowed(player.getWorld().getName())) return;

        // Don't start a new operation if one is already active
        if (breakingManager.hasActiveOperation(player.getUniqueId())) return;

        // Check broken block is an ore
        Block brokenBlock = event.getBlock();
        if (!OreGrouping.isOre(brokenBlock.getType())) return;

        // Find connected vein (excludes the start block)
        int maxBlocks = plugin.getConfigManager().getMaxBlocks(ToolType.VEIN_MINER);
        boolean groupDeepslate = plugin.getConfigManager().isVeinMinerGroupDeepslate();
        List<Block> vein = OreGrouping.findVein(brokenBlock, maxBlocks, groupDeepslate);

        // Filter by protection
        vein.removeIf(b -> !plugin.getProtectionService().canBreakBlock(player, b));

        if (vein.isEmpty()) return;

        // Suppress vanilla drops — we handle all drops via inventory
        event.setDropItems(false);

        // Handle the initial block's drops ourselves (direct to inventory)
        ItemStack fakeTool = DropHandler.getFakeTool(ToolType.VEIN_MINER);
        Collection<ItemStack> initialDrops = brokenBlock.getDrops(fakeTool, player);
        DropHandler.Result initialResult = DropHandler.distributeDrops(player, initialDrops);

        // Start breaking operation
        breakingManager.startOperation(player, ToolType.VEIN_MINER, vein,
                "veinminer-breaking", "veinminer-complete", 1, initialResult.overflowItems());
    }
}
