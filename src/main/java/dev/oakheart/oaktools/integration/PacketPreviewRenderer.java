package dev.oakheart.oaktools.integration;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.oakheart.oaktools.OakTools;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spawns and destroys invisible glowing slime entities via PacketEvents packets.
 * Entities exist only client-side — zero server overhead.
 */
public class PacketPreviewRenderer {

    // Entity metadata indices (Minecraft 1.21.x protocol)
    private static final int INDEX_BASE_FLAGS = 0;
    private static final int INDEX_SILENT = 4;
    private static final int INDEX_NO_GRAVITY = 5;
    private static final int INDEX_MOB_FLAGS = 15;
    private static final int INDEX_SLIME_SIZE = 16;

    // Base entity flags
    private static final byte FLAG_INVISIBLE = 0x20;
    private static final byte FLAG_GLOWING = 0x40;

    // Mob flags
    private static final byte MOB_FLAG_NO_AI = 0x01;

    // Pre-built metadata (identical for every preview entity)
    private static final List<EntityData<?>> SLIME_METADATA = List.of(
            new EntityData<>(INDEX_BASE_FLAGS, EntityDataTypes.BYTE, (byte) (FLAG_INVISIBLE | FLAG_GLOWING)),
            new EntityData<>(INDEX_SILENT, EntityDataTypes.BOOLEAN, true),
            new EntityData<>(INDEX_NO_GRAVITY, EntityDataTypes.BOOLEAN, true),
            new EntityData<>(INDEX_MOB_FLAGS, EntityDataTypes.BYTE, MOB_FLAG_NO_AI),
            new EntityData<>(INDEX_SLIME_SIZE, EntityDataTypes.INT, 2)
    );

    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(1_000_000_000);

    /**
     * Returns a unique entity ID for a packet-only entity.
     * Starts at 1 billion to avoid collisions with real server entity IDs.
     */
    public static int nextEntityId() {
        return ENTITY_ID_COUNTER.incrementAndGet();
    }

    /**
     * Spawns an invisible glowing slime at the given block-center position, visible only to the target player.
     */
    public static void spawnSlime(Player player, int entityId, UUID entityUuid, double x, double y, double z) {
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(entityUuid), EntityTypes.SLIME,
                new Vector3d(x, y, z),
                0f, 0f, 0f, 0, Optional.empty()
        );

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId, SLIME_METADATA
        );

        var playerManager = PacketEvents.getAPI().getPlayerManager();
        playerManager.sendPacket(player, spawnPacket);
        playerManager.sendPacket(player, metadataPacket);
    }

    /**
     * Sends a destroy entities packet to remove the given entity IDs from the player's client.
     */
    public static void destroy(Player player, int... entityIds) {
        if (entityIds.length == 0) return;
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityIds);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroyPacket);
    }

    /**
     * Creates and registers the packet listener that intercepts clicks on fake preview entities.
     * Returns an opaque handle for later unregistration.
     */
    public static PreviewPacketListener registerClickListener(OakTools plugin, Set<Integer> fakeEntityIds) {
        PreviewPacketListener listener = new PreviewPacketListener(plugin, fakeEntityIds);
        listener.register();
        return listener;
    }

    /**
     * Unregisters a previously registered click listener.
     */
    public static void unregisterClickListener(PreviewPacketListener listener) {
        if (listener != null) {
            listener.unregister();
        }
    }
}
