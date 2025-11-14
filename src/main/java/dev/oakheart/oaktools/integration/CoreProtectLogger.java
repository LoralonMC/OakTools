package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.OakTools;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * Handles async logging to CoreProtect for tool actions.
 */
public class CoreProtectLogger {

    private final OakTools plugin;
    private CoreProtectAPI coreProtectAPI;
    private boolean available;

    public CoreProtectLogger(OakTools plugin) {
        this.plugin = plugin;
        this.available = false;
    }

    /**
     * Initialize CoreProtect integration.
     */
    public void initialize() {
        if (!plugin.getConfigManager().getConfig().getBoolean("integration.coreprotect.enabled", true)) {
            plugin.getLogger().info("CoreProtect integration is disabled in config");
            return;
        }

        var coreProtectPlugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (!(coreProtectPlugin instanceof CoreProtect coreProtect)) {
            plugin.getLogger().info("CoreProtect not found - logging disabled");
            return;
        }

        CoreProtectAPI api = coreProtect.getAPI();
        if (api.isEnabled() && api.APIVersion() >= 9) {
            this.coreProtectAPI = api;
            this.available = true;
            plugin.getLogger().info("CoreProtect integration enabled (API v" + api.APIVersion() + ")");
        } else {
            plugin.getLogger().warning("CoreProtect API version too old or disabled");
        }
    }

    /**
     * Log a File tool edit (block state change).
     * Logs as break (old state) + place (new state) for rollback support.
     *
     * @param player the player
     * @param block the block
     * @param oldData the old block data
     * @param newData the new block data
     */
    public void logFileEdit(Player player, Block block, BlockData oldData, BlockData newData) {
        if (!available || !plugin.getConfigManager().getConfig()
                .getBoolean("integration.coreprotect.log_file_changes", true)) {
            return;
        }

        // Must run synchronously to access block entities (hoppers, chests, etc.)
        // CoreProtect's API is efficient enough that sync logging doesn't cause lag
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Log break (old state)
                coreProtectAPI.logRemoval(
                        player.getName(),
                        block.getLocation(),
                        oldData.getMaterial(),
                        oldData
                );

                // Log place (new state)
                coreProtectAPI.logPlacement(
                        player.getName(),
                        block.getLocation(),
                        newData.getMaterial(),
                        newData
                );
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to log File edit to CoreProtect: " + e.getMessage());
            }
        });
    }

    /**
     * Log a Trowel placement.
     *
     * @param player the player
     * @param block the block
     * @param blockData the placed block data
     */
    public void logTrowelPlacement(Player player, Block block, BlockData blockData) {
        if (!available || !plugin.getConfigManager().getConfig()
                .getBoolean("integration.coreprotect.log_trowel_placements", true)) {
            return;
        }

        // Must run synchronously to access block entities (if placing chests, hoppers, etc.)
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                coreProtectAPI.logPlacement(
                        player.getName(),
                        block.getLocation(),
                        blockData.getMaterial(),
                        blockData
                );
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to log Trowel placement to CoreProtect: " + e.getMessage());
            }
        });
    }

    /**
     * Check if CoreProtect integration is available.
     *
     * @return true if available
     */
    public boolean isAvailable() {
        return available;
    }
}
