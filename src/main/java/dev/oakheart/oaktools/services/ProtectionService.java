package dev.oakheart.oaktools.services;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProtectionService implements Listener {

    private static final String BYPASS_PERMISSION = "oaktools.bypass.protection";

    private final OakTools plugin;

    // True while a fake probe event is being dispatched. OakTools' own block
    // listeners must skip probe events: reacting to one re-enters the listener,
    // which recurses until StackOverflowError (and hands out real drops for
    // blocks that were never broken). Main-thread only.
    private boolean firingProbe = false;

    // When each player last had a probe fired on their behalf. A single wand
    // click probes up to max-blocks positions, so packet-rate anticheat checks
    // that count BlockPlace/BlockBreak events see a burst that no human could
    // produce by hand. VulcanFlagListener uses this to tell those false
    // positives apart from real fast-place/fast-break. Main-thread only.
    private final Map<UUID, Long> lastProbeAt = new HashMap<>();

    public ProtectionService(OakTools plugin) {
        this.plugin = plugin;
    }

    public boolean isFiringProbe() {
        return firingProbe;
    }

    /**
     * Whether a protection probe was fired for this player within the last
     * {@code millis} milliseconds.
     */
    public boolean probedWithin(Player player, long millis) {
        Long last = lastProbeAt.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last <= millis;
    }

    /**
     * Clear the probe marker, so a burst of probes excuses at most one
     * anticheat flag. Anticheat checks that bucket events over a time window
     * only close that bucket on the next block event, which can land well
     * after the player stopped using the tool — so the marker has to outlive
     * the probes, and consuming it is what stops it from excusing unrelated
     * flags later.
     */
    public void clearProbeMarker(Player player) {
        lastProbeAt.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastProbeAt.remove(event.getPlayer().getUniqueId());
    }

    public boolean canModifyBlock(Player player, Block block, EquipmentSlot hand, ItemStack tool) {
        plugin.debug("[Protection Debug] canModifyBlock called for block: " + block.getType());

        if (player.hasPermission(BYPASS_PERMISSION)) {
            plugin.debug("[Protection Debug] Player has bypass permission, allowing");
            return true;
        }

        plugin.debug("[Protection Debug] Creating fake BlockPlaceEvent with canBuild=false");

        BlockPlaceEvent fakeEvent = new BlockPlaceEvent(
                block,
                block.getState(),
                block.getRelative(BlockFace.DOWN),
                tool,
                player,
                false,
                hand
        );

        plugin.debug("[Protection Debug] Firing fake BlockPlaceEvent...");

        firingProbe = true;
        lastProbeAt.put(player.getUniqueId(), System.currentTimeMillis());
        try {
            plugin.getServer().getPluginManager().callEvent(fakeEvent);
        } finally {
            firingProbe = false;
        }

        boolean result = !fakeEvent.isCancelled();

        plugin.debug("[Protection Debug] Event fired. Cancelled: " + fakeEvent.isCancelled() + ", Result: " + result);

        return result;
    }

    /**
     * Check if a player can break a block, using a fake BlockBreakEvent
     * to let protection plugins (WorldGuard, GriefPrevention, Towny, etc.) deny it.
     */
    public boolean canBreakBlock(Player player, Block block) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return true;
        }

        BlockBreakEvent fakeEvent = new BlockBreakEvent(block, player);
        firingProbe = true;
        lastProbeAt.put(player.getUniqueId(), System.currentTimeMillis());
        try {
            plugin.getServer().getPluginManager().callEvent(fakeEvent);
        } finally {
            firingProbe = false;
        }
        return !fakeEvent.isCancelled();
    }
}
