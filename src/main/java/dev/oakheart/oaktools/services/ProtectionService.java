package dev.oakheart.oaktools.services;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ProtectionService {

    private static final String BYPASS_PERMISSION = "oaktools.bypass.protection";

    private final OakTools plugin;

    public ProtectionService(OakTools plugin) {
        this.plugin = plugin;
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

        plugin.getServer().getPluginManager().callEvent(fakeEvent);

        boolean result = !fakeEvent.isCancelled();

        plugin.debug("[Protection Debug] Event fired. Cancelled: " + fakeEvent.isCancelled() + ", Result: " + result);

        return result;
    }
}
