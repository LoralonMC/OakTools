package dev.oakheart.oaktools.integration;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import dev.oakheart.oaktools.OakTools;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.Set;

/**
 * Intercepts client-side INTERACT_ENTITY packets targeting fake preview entities.
 * Converts them into synthetic block interactions so the wand placement logic fires normally.
 */
public class PreviewPacketListener extends PacketListenerAbstract {

    private final OakTools plugin;
    private final Set<Integer> fakeEntityIds;

    public PreviewPacketListener(OakTools plugin, Set<Integer> fakeEntityIds) {
        this.plugin = plugin;
        this.fakeEntityIds = fakeEntityIds;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (!fakeEntityIds.contains(wrapper.getEntityId())) {
            return;
        }

        event.setCancelled(true);

        var action = wrapper.getAction();

        // INTERACT_AT is sent alongside INTERACT for right-clicks — skip to avoid double processing
        if (action == WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
            return;
        }

        Player player = (Player) event.getPlayer();

        if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            // Left-click: just clear the preview so next click hits the real block
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getWandPreviewManager().clearPreviewForPlayer(player));
            return;
        }

        // Right-click (INTERACT): clear preview and fire synthetic block interaction
        InteractionHand peHand = wrapper.getHand();
        EquipmentSlot hand = (peHand == InteractionHand.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;

        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getWandPreviewManager().clearPreviewForPlayer(player);

            RayTraceResult result = player.rayTraceBlocks(5.0);
            if (result == null || result.getHitBlock() == null || result.getHitBlockFace() == null) {
                return;
            }

            ItemStack item = player.getInventory().getItem(hand);
            PlayerInteractEvent synthetic = new PlayerInteractEvent(
                    player, Action.RIGHT_CLICK_BLOCK, item,
                    result.getHitBlock(), result.getHitBlockFace(), hand);
            Bukkit.getPluginManager().callEvent(synthetic);
        });
    }

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }
}
