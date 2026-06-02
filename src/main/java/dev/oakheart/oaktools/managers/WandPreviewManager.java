package dev.oakheart.oaktools.managers;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.integration.PacketPreviewRenderer;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.model.WandMode;
import dev.oakheart.oaktools.util.Constants;
import dev.oakheart.oaktools.util.InventoryUtil;
import dev.oakheart.oaktools.util.WandPlacementCalculator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows a per-cell wireframe preview of the blocks the Builder's Wand would place.
 *
 * <p>The preview is rendered with client-only packet block-displays and therefore requires
 * PacketEvents — without it the wand still works, only the preview is unavailable.
 */
public class WandPreviewManager implements Listener {

    private final OakTools plugin;
    private final Map<UUID, PreviewState> previews = new HashMap<>();
    private final WireframePreviewRenderer wireframeRenderer = new WireframePreviewRenderer();
    private BukkitTask previewTask;

    public WandPreviewManager(OakTools plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfigManager().isWandPreviewEnabled()) {
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            plugin.getLogger().info("PacketEvents not installed — Builder's Wand preview disabled");
            return;
        }

        int intervalTicks = plugin.getConfigManager().getWandPreviewIntervalTicks();
        previewTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (previewTask != null) {
            previewTask.cancel();
            previewTask = null;
        }

        for (var entry : previews.entrySet()) {
            removePreview(entry.getKey(), entry.getValue());
        }
        previews.clear();
    }

    public void restart() {
        stop();
        start();
    }

    public void clearPreviewForPlayer(Player player) {
        clearAndRemove(player.getUniqueId());
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            processPlayer(player);
        }
    }

    private void processPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        boolean hasPreview = previews.containsKey(uuid);

        // Player sneaking = about to undo or cycle mode, don't show preview
        if (player.isSneaking()) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        // Find which hand holds the wand
        WandInfo wandInfo = findWandInfo(player);
        if (wandInfo == null) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        if (!player.hasPermission("oaktools.use.wand")) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        if (!plugin.getConfigManager().isWandEnabled()) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        if (!plugin.getConfigManager().isGamemodeAllowed(player.getGameMode())) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        if (!plugin.getConfigManager().isWorldAllowed(player.getWorld().getName())) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        // Ray trace to find target block
        RayTraceResult rayTrace = player.rayTraceBlocks(5.0);
        if (rayTrace == null || rayTrace.getHitBlock() == null || rayTrace.getHitBlockFace() == null) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        Block targetBlock = rayTrace.getHitBlock();
        BlockFace targetFace = rayTrace.getHitBlockFace();

        if (!targetBlock.getType().isSolid()) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        // Check if target has changed since last tick (compare primitives, no allocation)
        int tx = targetBlock.getX();
        int ty = targetBlock.getY();
        int tz = targetBlock.getZ();
        PreviewState existingState = previews.get(uuid);
        if (existingState != null
                && existingState.valid()
                && existingState.lastX == tx
                && existingState.lastY == ty
                && existingState.lastZ == tz
                && existingState.lastTargetFace == targetFace
                && existingState.lastWandMode == wandInfo.mode) {
            return; // Nothing changed, keep existing preview
        }

        // Clear old preview
        if (existingState != null) {
            removePreview(uuid, existingState);
        }

        // Calculate new placements
        Material sourceMaterial = targetBlock.getType();
        int maxBlocks = plugin.getConfigManager().getWandMaxBlocks();

        // Resolve consume material (for inventory count limiting)
        Material consumeMaterial = sourceMaterial;
        ItemStack overrideItem = InventoryUtil.resolveOverrideBlock(player, wandInfo.hand, plugin.getConfigManager());
        if (overrideItem != null) {
            consumeMaterial = overrideItem.getType();
        }

        List<Block> placements;
        if (wandInfo.mode == WandMode.FACE) {
            placements = WandPlacementCalculator.buildFacePlacementPreview(
                    targetBlock, targetFace, sourceMaterial, maxBlocks, plugin);
        } else {
            placements = WandPlacementCalculator.buildLinePlacementPreview(
                    targetBlock, targetFace, sourceMaterial, maxBlocks, player, plugin);
        }

        if (placements.isEmpty()) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        // Limit preview to available blocks if consuming
        boolean consumeBlocks = plugin.getConfigManager().shouldConsumeBlocks(player.getGameMode());
        if (consumeBlocks) {
            int available = InventoryUtil.countMaterial(player, consumeMaterial);
            if (available == 0) {
                if (hasPreview) clearAndRemove(uuid);
                return;
            }
            if (available < placements.size()) {
                placements = placements.subList(0, available);
            }
        }

        int[] entityIds = wireframeRenderer.spawn(
                player, placements,
                plugin.getConfigManager().getWandPreviewLineColor(),
                plugin.getConfigManager().getWandPreviewLineThickness());

        if (entityIds.length == 0) {
            return;
        }
        previews.put(uuid, new PreviewState(entityIds, tx, ty, tz, targetFace, wandInfo.mode));
    }

    // ===== Preview removal =====

    private void clearAndRemove(UUID uuid) {
        PreviewState state = previews.remove(uuid);
        if (state != null) {
            removePreview(uuid, state);
        }
    }

    private void removePreview(UUID playerId, PreviewState state) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && state.entityIds().length > 0) {
            PacketPreviewRenderer.destroy(player, state.entityIds());
        }
    }

    // ===== Wand detection =====

    private WandInfo findWandInfo(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isWand(mainHand)) {
            return new WandInfo(EquipmentSlot.HAND, getWandMode(mainHand));
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isWand(offHand)) {
            return new WandInfo(EquipmentSlot.OFF_HAND, getWandMode(offHand));
        }

        return null;
    }

    private boolean isWand(ItemStack item) {
        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return false;
        }
        return plugin.getItemFactory().getToolType(item) == ToolType.WAND;
    }

    private WandMode getWandMode(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return WandMode.FACE;
        }
        String modeString = meta.getPersistentDataContainer()
                .get(Constants.WAND_MODE, PersistentDataType.STRING);
        return WandMode.fromString(modeString);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearAndRemove(event.getPlayer().getUniqueId());
    }

    // ===== Inner types =====

    private record WandInfo(EquipmentSlot hand, WandMode mode) {}

    private record PreviewState(int[] entityIds,
                                int lastX, int lastY, int lastZ,
                                BlockFace lastTargetFace, WandMode lastWandMode) {
        boolean valid() {
            return entityIds.length > 0;
        }
    }
}
