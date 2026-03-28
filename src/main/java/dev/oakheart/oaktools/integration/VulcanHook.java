package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.OakTools;
import me.frep.vulcan.api.event.VulcanFlagEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vulcan anticheat integration.
 * Temporarily exempts players from FastPlace checks during OakTools block operations.
 */
public class VulcanHook implements Listener {

    private static final long EXEMPTION_TICKS = 10L;

    private final OakTools plugin;
    private final Map<UUID, Integer> exemptionCounts = new ConcurrentHashMap<>();

    public VulcanHook(OakTools plugin) {
        this.plugin = plugin;
    }

    /**
     * Register the Vulcan event listener.
     * Must only be called after confirming Vulcan is present.
     */
    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Vulcan detected — anticheat exemptions enabled for tool usage.");
    }

    /**
     * Temporarily exempt a player from placement-related anticheat checks.
     * Exemption automatically expires after a short delay.
     * Uses a reference counter so overlapping exemptions don't cancel each other.
     */
    public void exempt(Player player) {
        UUID uuid = player.getUniqueId();
        exemptionCounts.merge(uuid, 1, Integer::sum);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            exemptionCounts.computeIfPresent(uuid, (k, v) -> v <= 1 ? null : v - 1);
        }, EXEMPTION_TICKS);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVulcanFlag(VulcanFlagEvent event) {
        if (exemptionCounts.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            plugin.debug("[Vulcan Debug] Cancelled " + event.getCheck().getName() +
                    " flag for " + event.getPlayer().getName() + " during tool operation");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVulcanSetback(me.frep.vulcan.api.event.VulcanSetbackEvent event) {
        if (exemptionCounts.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
