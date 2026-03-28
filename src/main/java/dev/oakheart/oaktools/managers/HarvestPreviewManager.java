package dev.oakheart.oaktools.managers;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.integration.PacketPreviewRenderer;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.Constants;
import dev.oakheart.oaktools.util.ExcavationCalculator;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Glow outline preview for harvesting tools.
 * Shows which blocks will be affected when a player looks at a target block.
 * Uses packet-based invisible glowing slimes (same as wand preview).
 */
public class HarvestPreviewManager implements Listener {

    private static final String TEAM_NAME = "oaktools_harvest_preview";
    private static final Set<ToolType> HARVEST_TOOLS = Set.of(
            ToolType.EXCAVATOR, ToolType.LUMBERJACK, ToolType.VEIN_MINER);

    private final OakTools plugin;
    private BreakingAnimationManager breakingAnimationManager;
    private final Map<UUID, PreviewState> previews = new HashMap<>();
    private BukkitTask previewTask;
    private Team previewTeam;
    private boolean usePackets;

    // Thread-safe set for packet listener (if needed)
    private final Set<Integer> fakeEntityIds = ConcurrentHashMap.newKeySet();

    public HarvestPreviewManager(OakTools plugin) {
        this.plugin = plugin;
    }

    public void setBreakingAnimationManager(BreakingAnimationManager breakingAnimationManager) {
        this.breakingAnimationManager = breakingAnimationManager;
    }

    public void start() {
        usePackets = Bukkit.getPluginManager().getPlugin("packetevents") != null;
        if (!usePackets) {
            plugin.getLogger().info("PacketEvents not found — harvest tool preview disabled");
            return;
        }

        // Set up scoreboard team for glow color (uses excavator color as default)
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        previewTeam = scoreboard.getTeam(TEAM_NAME);
        if (previewTeam == null) {
            previewTeam = scoreboard.registerNewTeam(TEAM_NAME);
        }
        previewTeam.color(NamedTextColor.YELLOW);
        previewTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        previewTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
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
        fakeEntityIds.clear();

        if (previewTeam != null) {
            previewTeam.unregister();
            previewTeam = null;
        }
    }

    public void restart() {
        stop();
        start();
    }

    public void clearPreviewForPlayer(Player player) {
        PreviewState state = previews.remove(player.getUniqueId());
        if (state != null) {
            removePreview(player.getUniqueId(), state);
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            processPlayer(player);
        }
    }

    private void processPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        boolean hasPreview = previews.containsKey(uuid);

        // Skip if sneaking, has active breaking operation, or wrong gamemode/world
        if (player.isSneaking()) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        if (breakingAnimationManager != null
                && breakingAnimationManager.hasActiveOperation(uuid)) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        // Find which harvest tool is held
        ToolType toolType = getHeldHarvestTool(player);
        if (toolType == null) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        if (!plugin.getConfigManager().isHarvestToolEnabled(toolType)) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        if (!plugin.getConfigManager().isHarvestPreviewEnabled(toolType)) {
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

        // Ray trace to target block
        RayTraceResult rayTrace = player.rayTraceBlocks(5.0);
        if (rayTrace == null || rayTrace.getHitBlock() == null || rayTrace.getHitBlockFace() == null) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        Block targetBlock = rayTrace.getHitBlock();
        BlockFace targetFace = rayTrace.getHitBlockFace();

        // Check if target is valid for this tool
        if (!isValidTarget(targetBlock, toolType)) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        // Change detection
        int tx = targetBlock.getX(), ty = targetBlock.getY(), tz = targetBlock.getZ();
        PreviewState existing = previews.get(uuid);
        if (existing != null
                && existing.lastX == tx && existing.lastY == ty && existing.lastZ == tz
                && existing.lastFace == targetFace && existing.toolType == toolType) {
            return; // Nothing changed
        }

        // Clear old preview
        if (existing != null) {
            removePreview(uuid, existing);
        }

        // Calculate affected blocks, excluding the center block (player already sees
        // the vanilla selection outline on it, and a slime there would intercept left-clicks)
        List<Block> blocks = calculateAffectedBlocks(player, targetBlock, targetFace, toolType);
        blocks.removeIf(b -> b.equals(targetBlock));
        if (blocks.isEmpty()) {
            if (hasPreview) clearAndRemove(uuid);
            return;
        }

        // Update team color for this tool type
        NamedTextColor color = plugin.getConfigManager().getHarvestPreviewGlowColor(toolType);
        if (previewTeam != null) {
            previewTeam.color(color);
        }

        // Spawn packet-based preview entities
        List<FakeEntity> entities = new ArrayList<>();
        for (Block block : blocks) {
            int entityId = PacketPreviewRenderer.nextEntityId();
            UUID entityUuid = UUID.randomUUID();
            PacketPreviewRenderer.spawnSlime(player, entityId, entityUuid,
                    block.getX() + 0.5, block.getY(), block.getZ() + 0.5);
            fakeEntityIds.add(entityId);
            entities.add(new FakeEntity(entityId, entityUuid));
            if (previewTeam != null) {
                previewTeam.addEntry(entityUuid.toString());
            }
        }

        previews.put(uuid, new PreviewState(entities, tx, ty, tz, targetFace, toolType));
    }

    private ToolType getHeldHarvestTool(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!plugin.getItemFactory().isTool(item)) {
            return null;
        }
        ToolType type = plugin.getItemFactory().getToolType(item);
        return HARVEST_TOOLS.contains(type) ? type : null;
    }

    private boolean isValidTarget(Block block, ToolType toolType) {
        return switch (toolType) {
            case EXCAVATOR -> Tag.MINEABLE_SHOVEL.isTagged(block.getType());
            // Lumberjack and VeinMiner targets will be added when those tools are implemented
            default -> false;
        };
    }

    private List<Block> calculateAffectedBlocks(Player player, Block target, BlockFace face, ToolType toolType) {
        return switch (toolType) {
            case EXCAVATOR -> ExcavationCalculator.calculate(target, face,
                    plugin.getConfigManager().getMaxBlocks(ToolType.EXCAVATOR));
            default -> List.of();
        };
    }

    // ===== Preview removal =====

    private void clearAndRemove(UUID uuid) {
        PreviewState state = previews.remove(uuid);
        if (state != null) {
            removePreview(uuid, state);
        }
    }

    private void removePreview(UUID playerId, PreviewState state) {
        for (FakeEntity entity : state.entities) {
            fakeEntityIds.remove(entity.entityId);
            if (previewTeam != null) {
                previewTeam.removeEntry(entity.entityUuid.toString());
            }
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && !state.entities.isEmpty()) {
            int[] ids = state.entities.stream().mapToInt(FakeEntity::entityId).toArray();
            PacketPreviewRenderer.destroy(player, ids);
        }
    }

    // ===== Event handlers =====

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearAndRemove(event.getPlayer().getUniqueId());
    }

    // ===== Inner types =====

    private record FakeEntity(int entityId, UUID entityUuid) {}

    private record PreviewState(List<FakeEntity> entities,
                                int lastX, int lastY, int lastZ,
                                BlockFace lastFace, ToolType toolType) {}
}
