package dev.oakheart.oaktools.services;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles protection checks via fake events for plugin compatibility.
 */
public class ProtectionService {

    private static final String BYPASS_PERMISSION = "oaktools.bypass.protection";

    private final OakTools plugin;

    public ProtectionService(OakTools plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if a player can build at a block location (for File tool).
     * Creates a fake BlockPlaceEvent to check protection.
     *
     * NOTE: The fake event is created with canBuild=false to prevent Minecraft from
     * playing block place sounds. Protection plugins only check the cancelled state.
     *
     * @param player the player
     * @param block the block to check
     * @param hand the equipment slot used
     * @param tool the tool item
     * @return true if the player can build, false otherwise
     */
    public boolean canModifyBlock(Player player, Block block, EquipmentSlot hand, ItemStack tool) {
        boolean debug = plugin.getConfigManager().getConfig().getBoolean("general.debug", false);

        if (debug) {
            plugin.getLogger().info("[Protection Debug] canModifyBlock called for block: " + block.getType());
        }

        // Check bypass permission
        if (player.hasPermission(BYPASS_PERMISSION)) {
            if (debug) {
                plugin.getLogger().info("[Protection Debug] Player has bypass permission, allowing");
            }
            return true;
        }

        if (debug) {
            plugin.getLogger().info("[Protection Debug] Creating fake BlockPlaceEvent with canBuild=false");
        }

        // Create fake BlockPlaceEvent to check protection
        // IMPORTANT: canBuild=false prevents client sounds while still allowing protection checks
        BlockPlaceEvent fakeEvent = new BlockPlaceEvent(
                block,                          // Block placed
                block.getState(),              // Previous state
                block.getRelative(BlockFace.DOWN), // Block placed against (approximation)
                tool,                          // Item in hand
                player,                        // Player
                false,                         // Can build (FALSE to prevent sounds!)
                hand                           // Hand used
        );

        if (debug) {
            plugin.getLogger().info("[Protection Debug] Firing fake BlockPlaceEvent...");
        }

        // Call the event (let protection plugins decide)
        plugin.getServer().getPluginManager().callEvent(fakeEvent);

        boolean result = !fakeEvent.isCancelled();

        if (debug) {
            plugin.getLogger().info("[Protection Debug] Event fired. Cancelled: " + fakeEvent.isCancelled() + ", Result: " + result);
        }

        // If protection plugin cancelled it, deny the action
        // Note: We check !isCancelled() because canBuild starts as false
        return result;
    }
}
