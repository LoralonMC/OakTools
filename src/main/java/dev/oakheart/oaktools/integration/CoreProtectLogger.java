package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.OakTools;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

public class CoreProtectLogger {

    private final OakTools plugin;
    private CoreProtectAPI coreProtectAPI;
    private boolean available;

    public CoreProtectLogger(OakTools plugin) {
        this.plugin = plugin;
        this.available = false;
    }

    public void initialize() {
        if (!plugin.getConfigManager().isCoreProtectEnabled()) {
            plugin.getLogger().info("CoreProtect integration is disabled in config");
            return;
        }

        var coreProtectPlugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (!(coreProtectPlugin instanceof CoreProtect coreProtect)) {
            plugin.getLogger().info("CoreProtect not found — logging disabled");
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

    public void logFileEdit(Player player, Block block, BlockData oldData, BlockData newData) {
        if (!available || !plugin.getConfigManager().isLogFileChanges()) {
            return;
        }

        try {
            coreProtectAPI.logRemoval(
                    player.getName(),
                    block.getLocation(),
                    oldData.getMaterial(),
                    oldData
            );

            coreProtectAPI.logPlacement(
                    player.getName(),
                    block.getLocation(),
                    newData.getMaterial(),
                    newData
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to log File edit to CoreProtect: " + e.getMessage());
        }
    }

    public void logTrowelPlacement(Player player, Block block, BlockData blockData) {
        if (!available || !plugin.getConfigManager().isLogTrowelPlacements()) {
            return;
        }

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
    }

    public void logWandPlacement(Player player, Block block, BlockData blockData) {
        if (!available || !plugin.getConfigManager().isLogWandPlacements()) {
            return;
        }

        try {
            coreProtectAPI.logPlacement(
                    player.getName(),
                    block.getLocation(),
                    blockData.getMaterial(),
                    blockData
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to log Wand placement to CoreProtect: " + e.getMessage());
        }
    }

    public void logWandRemoval(Player player, Block block, BlockData blockData) {
        if (!available || !plugin.getConfigManager().isLogWandPlacements()) {
            return;
        }

        try {
            coreProtectAPI.logRemoval(
                    player.getName(),
                    block.getLocation(),
                    blockData.getMaterial(),
                    blockData
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to log Wand removal to CoreProtect: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }
}
