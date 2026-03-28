package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.OakTools;
import me.frep.vulcan.api.event.VulcanFlagEvent;
import me.frep.vulcan.api.event.VulcanPunishEvent;
import me.frep.vulcan.api.event.VulcanSetbackEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionAttachment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vulcan anticheat integration.
 * Temporarily grants vulcan.bypass permission during OakTools block operations.
 * Also cancels VulcanFlagEvent/PunishEvent/SetbackEvent as a fallback.
 */
public class VulcanHook implements Listener {

    private static final long EXEMPTION_TICKS = 10L;

    private final OakTools plugin;
    private final Map<UUID, PermissionAttachment> activeBypass = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> exemptionCounts = new ConcurrentHashMap<>();

    public VulcanHook(OakTools plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Vulcan detected — anticheat exemptions enabled for tool usage.");
    }

    /**
     * Temporarily exempt a player from all Vulcan checks during a tool operation.
     * Grants vulcan.bypass permission (respected at packet level) and registers
     * event-based cancellation as a fallback.
     */
    public void exempt(Player player) {
        UUID uuid = player.getUniqueId();

        // Grant vulcan.bypass permission temporarily
        if (!activeBypass.containsKey(uuid)) {
            PermissionAttachment attachment = player.addAttachment(plugin);
            attachment.setPermission("vulcan.bypass", true);
            activeBypass.put(uuid, attachment);
        }

        // Reference-counted exemption for event-based fallback
        exemptionCounts.merge(uuid, 1, Integer::sum);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Remove permission bypass
            Integer count = exemptionCounts.computeIfPresent(uuid, (k, v) -> v <= 1 ? null : v - 1);
            if (count == null) {
                PermissionAttachment attachment = activeBypass.remove(uuid);
                if (attachment != null) {
                    attachment.remove();
                }
            }
        }, EXEMPTION_TICKS);
    }

    // Fallback: cancel flags, punishments, and setbacks via event API

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVulcanFlag(VulcanFlagEvent event) {
        if (exemptionCounts.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVulcanPunish(VulcanPunishEvent event) {
        if (exemptionCounts.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVulcanSetback(VulcanSetbackEvent event) {
        if (exemptionCounts.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
