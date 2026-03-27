package dev.oakheart.oaktools.managers;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.integration.PacketPreviewRenderer;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.model.WandMode;
import dev.oakheart.oaktools.util.Constants;
import dev.oakheart.oaktools.util.InventoryUtil;
import dev.oakheart.oaktools.util.WandPlacementCalculator;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WandPreviewManager implements Listener {

    private static final String TEAM_NAME = "oaktools_preview";

    private final OakTools plugin;
    private final Map<UUID, PreviewState> previews = new HashMap<>();
    private BukkitTask previewTask;
    private Team previewTeam;

    // Packet-based preview (when PacketEvents is available)
    private boolean usePackets;
    private final Set<Integer> fakeEntityIds = ConcurrentHashMap.newKeySet();
    private Object packetClickListener; // PreviewPacketListener, stored as Object to avoid class loading

    // Slime-based fallback state (only used when PacketEvents is absent)
    private final Set<UUID> slimeEntityIds = new HashSet<>();

    public WandPreviewManager(OakTools plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfigManager().isWandPreviewEnabled()) {
            return;
        }

        // Detect PacketEvents for packet-based preview
        usePackets = Bukkit.getPluginManager().getPlugin("packetevents") != null;
        if (usePackets) {
            packetClickListener = PacketPreviewRenderer.registerClickListener(plugin, fakeEntityIds);
            plugin.getLogger().info("PacketEvents detected — using packet-based wand preview");
        }

        // Set up scoreboard team for glow color
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        previewTeam = scoreboard.getTeam(TEAM_NAME);
        if (previewTeam == null) {
            previewTeam = scoreboard.registerNewTeam(TEAM_NAME);
        }
        previewTeam.color(plugin.getConfigManager().getWandPreviewGlowColor());
        previewTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

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
        fakeEntityIds.clear();
        slimeEntityIds.clear();

        if (usePackets && packetClickListener != null) {
            PacketPreviewRenderer.unregisterClickListener(
                    (dev.oakheart.oaktools.integration.PreviewPacketListener) packetClickListener);
            packetClickListener = null;
        }

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
                && existingState.entitiesValid(usePackets)
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

        if (usePackets) {
            spawnPacketPreview(player, uuid, placements, tx, ty, tz, targetFace, wandInfo.mode);
        } else {
            spawnSlimePreview(player, uuid, placements, tx, ty, tz, targetFace, wandInfo.mode);
        }
    }

    // ===== Packet-based preview (PacketEvents) =====

    private void spawnPacketPreview(Player player, UUID uuid, List<Block> placements,
                                    int tx, int ty, int tz, BlockFace targetFace, WandMode wandMode) {
        List<FakeEntity> entities = new ArrayList<>();

        for (Block block : placements) {
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

        previews.put(uuid, new PreviewState(null, entities, tx, ty, tz, targetFace, wandMode));
    }

    // ===== Slime-based preview (fallback) =====

    private void spawnSlimePreview(Player player, UUID uuid, List<Block> placements,
                                   int tx, int ty, int tz, BlockFace targetFace, WandMode wandMode) {
        List<Slime> slimes = new ArrayList<>();

        for (Block block : placements) {
            Location spawnLoc = block.getLocation().add(0.5, 0, 0.5);
            Slime slime = block.getWorld().spawn(spawnLoc, Slime.class, entity -> {
                entity.setSize(2);
                entity.setAI(false);
                entity.setSilent(true);
                entity.setInvulnerable(true);
                entity.setGravity(false);
                entity.setInvisible(true);
                entity.setGlowing(true);
                entity.setCollidable(false);
                entity.setVisibleByDefault(false);
                entity.setPersistent(false);
                entity.customName(Component.text("oaktools_preview"));
                entity.setCustomNameVisible(false);
                entity.getPersistentDataContainer().set(
                        Constants.PREVIEW_ENTITY, PersistentDataType.BYTE, (byte) 1);
                entity.addScoreboardTag("oaktools_preview");
            });
            slimeEntityIds.add(slime.getUniqueId());
            if (previewTeam != null) {
                previewTeam.addEntry(slime.getUniqueId().toString());
            }
            player.showEntity(plugin, slime);
            slimes.add(slime);
        }

        previews.put(uuid, new PreviewState(slimes, null, tx, ty, tz, targetFace, wandMode));
    }

    // ===== Preview removal =====

    private void clearAndRemove(UUID uuid) {
        PreviewState state = previews.remove(uuid);
        if (state != null) {
            removePreview(uuid, state);
        }
    }

    private void removePreview(UUID playerId, PreviewState state) {
        if (state.fakeEntities() != null) {
            // Packet mode: send destroy packets and clean up team entries
            for (FakeEntity entity : state.fakeEntities()) {
                fakeEntityIds.remove(entity.entityId());
                if (previewTeam != null) {
                    previewTeam.removeEntry(entity.entityUuid().toString());
                }
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && !state.fakeEntities().isEmpty()) {
                int[] ids = state.fakeEntities().stream()
                        .mapToInt(FakeEntity::entityId).toArray();
                PacketPreviewRenderer.destroy(player, ids);
            }
        } else if (state.slimeEntities() != null) {
            // Slime mode: remove real entities
            for (Slime slime : state.slimeEntities()) {
                slimeEntityIds.remove(slime.getUniqueId());
                if (previewTeam != null) {
                    previewTeam.removeEntry(slime.getUniqueId().toString());
                }
                if (slime.isValid()) {
                    slime.remove();
                }
            }
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

    // ===== Event handlers (slime fallback only — packet entities can't be interacted with) =====

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreviewEntityInteract(PlayerInteractEntityEvent event) {
        if (!slimeEntityIds.contains(event.getRightClicked().getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        clearPreviewForPlayer(player);

        // Ray trace to find the block the player is actually looking at
        RayTraceResult result = player.rayTraceBlocks(5.0);
        if (result == null || result.getHitBlock() == null || result.getHitBlockFace() == null) {
            return;
        }

        // Fire synthetic block interaction so the wand (and other plugins) process it normally
        EquipmentSlot hand = event.getHand();
        ItemStack item = player.getInventory().getItem(hand);
        PlayerInteractEvent synthetic = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, item,
                result.getHitBlock(), result.getHitBlockFace(), hand);
        Bukkit.getPluginManager().callEvent(synthetic);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreviewEntityDamage(EntityDamageByEntityEvent event) {
        if (!slimeEntityIds.contains(event.getEntity().getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        // Clear preview so the next click hits the actual block
        if (event.getDamager() instanceof Player player) {
            clearPreviewForPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearAndRemove(event.getPlayer().getUniqueId());
    }

    // ===== Inner types =====

    private record FakeEntity(int entityId, UUID entityUuid) {}

    private record WandInfo(EquipmentSlot hand, WandMode mode) {}

    private record PreviewState(List<Slime> slimeEntities, List<FakeEntity> fakeEntities,
                                int lastX, int lastY, int lastZ,
                                BlockFace lastTargetFace, WandMode lastWandMode) {
        boolean entitiesValid(boolean packetMode) {
            if (packetMode && fakeEntities != null) {
                return !fakeEntities.isEmpty();
            }
            return slimeEntities != null && !slimeEntities.isEmpty() && slimeEntities.getFirst().isValid();
        }
    }
}
