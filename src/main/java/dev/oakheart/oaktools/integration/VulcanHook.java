package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Soft dependency integration with the Vulcan anticheat.
 *
 * <p>OakTools asks protection plugins for permission by firing a throwaway
 * BlockPlaceEvent / BlockBreakEvent per candidate block (see ProtectionService).
 * Every other BlockPlace/BlockBreak listener on the server sees those probes
 * too, and Vulcan's Fast Place / Fast Break checks simply count those events
 * per second. One Builder's Wand click probes up to {@code tools.wand.max-blocks}
 * positions, so a few clicks a second sail past Vulcan's threshold and the
 * player is punished for blocks they never placed.
 *
 * <p>This hook cancels those specific flags while the player is mid-probe.
 * Real Vulcan detections, and every other check, are left untouched.
 *
 * <p>The listener lives in its own class so the Vulcan API types are only
 * loaded when Vulcan is actually installed.
 */
public class VulcanHook {

    private final OakTools plugin;
    private Listener listener;

    public VulcanHook(OakTools plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        unregister();

        if (!plugin.getConfigManager().isVulcanIntegrationEnabled()) {
            return;
        }

        Plugin vulcan = Bukkit.getPluginManager().getPlugin("Vulcan");
        if (vulcan == null || !vulcan.isEnabled()) {
            return;
        }

        try {
            listener = new VulcanFlagListener(plugin);
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            plugin.getLogger().info("Vulcan integration enabled (suppressing false Fast Place/Fast Break flags)");
        } catch (Throwable t) {
            // NoClassDefFoundError if the installed Vulcan build drops the API.
            listener = null;
            plugin.getLogger().log(Level.WARNING, "Failed to initialize Vulcan integration", t);
        }
    }

    public void unregister() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    public boolean isAvailable() {
        return listener != null;
    }
}
