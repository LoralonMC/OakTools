package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.CopperOxidation;
import dev.oakheart.oaktools.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Weathers copper forward one stage per right-click.
 *
 * <p>Forward only: scraping copper back a stage is already free with an axe, so
 * the tool exists to cover the direction vanilla gives no answer to except
 * waiting on random ticks. One block per click rather than an area, to match the
 * File's role as the precise tool.
 */
public class PatinaListener implements Listener {

    private final OakTools plugin;

    public PatinaListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPatinaUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand();

        if (item == null || !plugin.getItemFactory().isTool(item)) {
            return;
        }
        if (plugin.getItemFactory().getToolType(item) != ToolType.PATINA) {
            return;
        }

        if (!player.hasPermission("oaktools.use.patina")) {
            plugin.getMessageManager().send(player, "no-permission");
            event.setCancelled(true);
            return;
        }
        if (!plugin.getConfigManager().isPatinaEnabled()) {
            return;
        }
        if (!plugin.getConfigManager().isGamemodeAllowed(player.getGameMode())) {
            plugin.getMessageManager().send(player, "gamemode-denied");
            event.setCancelled(true);
            return;
        }
        if (!plugin.getConfigManager().isWorldAllowed(player.getWorld().getName())) {
            plugin.getMessageManager().send(player, "world-denied");
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            // Swallow the swing so the tool never does anything unintended off-block.
            event.setCancelled(true);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        handleRightClick(event, player, item, hand);
    }

    private void handleRightClick(PlayerInteractEvent event, Player player, ItemStack item, EquipmentSlot hand) {
        Block block = event.getClickedBlock();
        Material current = block.getType();
        Material next = CopperOxidation.next(current);

        if (next == null) {
            // Nothing to do. Cancel anyway so the click never falls through to a
            // vanilla interaction (opening a copper door, toggling a bulb).
            plugin.debug("[Patina Debug] " + current + " has no further stage");
            event.setCancelled(true);
            return;
        }

        if (!plugin.getProtectionService().canModifyBlock(player, block, hand, item)) {
            plugin.debug("[Patina Debug] Blocked: Protection denied");
            plugin.getMessageManager().send(player, "protection-denied");
            event.setCancelled(true);
            return;
        }

        BlockData oldData = block.getBlockData().clone();

        // Carry the existing block data across so a stair keeps its shape, a slab
        // its half, a door its hinge. setType would reset all of it to defaults.
        BlockData newData = next.createBlockData();
        newData = mergeState(oldData, newData);
        block.setBlockData(newData, false);

        event.setCancelled(true);

        boolean broken = plugin.getDurabilityService().damage(item, player, 1);
        if (!broken) {
            plugin.getDisplayService().updateDisplay(item);
        }

        spawnEffect(block);
        SoundUtil.playPlaceSound(block, newData, plugin);
        plugin.getCoreProtectLogger().logFileEdit(player, block, oldData, block.getBlockData());

        plugin.debug("[Patina Debug] " + current + " -> " + next);
    }

    /**
     * Copies the old block's state onto the new stage where both share it.
     * Every copper family keeps the same properties across stages, so this is a
     * straight string merge via the block data's serialised form.
     */
    private BlockData mergeState(BlockData oldData, BlockData newData) {
        String oldString = oldData.getAsString();
        int oldBracket = oldString.indexOf('[');
        if (oldBracket < 0) {
            return newData;   // the old block had no state to carry
        }
        String states = oldString.substring(oldBracket);
        String newKey = newData.getAsString();
        int newBracket = newKey.indexOf('[');
        if (newBracket >= 0) {
            newKey = newKey.substring(0, newBracket);
        }
        try {
            return org.bukkit.Bukkit.createBlockData(newKey + states);
        } catch (IllegalArgumentException e) {
            // Stages disagree on some property. Keep the stage change and accept
            // default state rather than failing the interaction outright.
            plugin.debug("[Patina Debug] State merge failed (" + e.getMessage() + "), using defaults");
            return newData;
        }
    }

    private void spawnEffect(Block block) {
        Particle particle = plugin.getConfigManager().getPatinaParticle();
        if (particle == null) {
            return;
        }
        int count = plugin.getConfigManager().getPatinaParticleCount();
        double spread = plugin.getConfigManager().getPatinaParticleSpread();
        block.getWorld().spawnParticle(
                particle,
                block.getLocation().add(0.5, 1.0, 0.5),
                count, spread, spread, spread, 0.0);
    }
}
