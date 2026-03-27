package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Soft dependency integration with OakOverflow via reflection.
 * Sends overflow items to OakOverflow's storage when a player's inventory is full.
 */
public class OverflowHook {

    private final OakTools plugin;
    private boolean available;
    private Object api;
    private Method addOverflowItemMethod;

    public OverflowHook(OakTools plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        available = false;
        api = null;
        addOverflowItemMethod = null;

        Plugin overflowPlugin = Bukkit.getPluginManager().getPlugin("OakOverflow");
        if (overflowPlugin == null || !overflowPlugin.isEnabled()) {
            return;
        }

        try {
            Method getApiMethod = overflowPlugin.getClass().getMethod("getAPI");
            api = getApiMethod.invoke(overflowPlugin);
            if (api == null) {
                plugin.getLogger().warning("OakOverflow API returned null");
                return;
            }

            addOverflowItemMethod = api.getClass().getMethod(
                    "addOverflowItem", Player.class, ItemStack.class, int.class, String.class);
            available = true;
            plugin.getLogger().info("OakOverflow integration enabled");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize OakOverflow integration", e);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Sends items to OakOverflow storage. Async fire-and-forget with error logging.
     */
    @SuppressWarnings("unchecked")
    public void sendToOverflow(Player player, ItemStack item, int amount) {
        if (!available || api == null || addOverflowItemMethod == null) {
            return;
        }

        try {
            CompletableFuture<?> future = (CompletableFuture<?>) addOverflowItemMethod.invoke(
                    api, player, item, amount, "OakTools");
            future.exceptionally(throwable -> {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to send item to OakOverflow for " + player.getName(), (Throwable) throwable);
                return null;
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to invoke OakOverflow API", e);
        }
    }
}
