package dev.oakheart.oaktools.integration;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spawns and destroys client-only block-display "beam" entities via PacketEvents packets.
 * Entities exist only on the target player's client — zero server overhead.
 */
public class PacketPreviewRenderer {

    // Display entity metadata indices (Minecraft 1.21.x protocol)
    private static final int INDEX_DISPLAY_TRANSLATION = 11;
    private static final int INDEX_DISPLAY_SCALE = 12;
    private static final int INDEX_DISPLAY_BRIGHTNESS = 16;
    private static final int INDEX_BLOCK_DISPLAY_STATE = 23;

    // Packed brightness: blockLight in bits 4-7, skyLight in bits 20-23. Full-bright = 15/15.
    private static final int FULL_BRIGHT = (15 << 4) | (15 << 20);

    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(1_000_000_000);

    /**
     * Returns a unique entity ID for a packet-only entity.
     * Starts at 1 billion to avoid collisions with real server entity IDs.
     */
    public static int nextEntityId() {
        return ENTITY_ID_COUNTER.incrementAndGet();
    }

    /**
     * Spawns a thin block-display "beam" at the given position, visible only to the target player.
     * The translation/scale turn the unit block model into a beam; {@code blockStateId} is the
     * global palette id of the block to display (see {@link #blockStateId}).
     */
    public static void spawnBlockDisplay(Player player, int entityId, UUID entityUuid,
                                         double x, double y, double z,
                                         float tx, float ty, float tz,
                                         float sx, float sy, float sz, int blockStateId) {
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(entityUuid), EntityTypes.BLOCK_DISPLAY,
                new Vector3d(x, y, z),
                0f, 0f, 0f, 0, Optional.empty()
        );

        List<EntityData<?>> metadata = List.of(
                new EntityData<>(INDEX_DISPLAY_TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(tx, ty, tz)),
                new EntityData<>(INDEX_DISPLAY_SCALE, EntityDataTypes.VECTOR3F, new Vector3f(sx, sy, sz)),
                new EntityData<>(INDEX_DISPLAY_BRIGHTNESS, EntityDataTypes.INT, FULL_BRIGHT),
                new EntityData<>(INDEX_BLOCK_DISPLAY_STATE, EntityDataTypes.BLOCK_STATE, blockStateId)
        );

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId, metadata
        );

        var playerManager = PacketEvents.getAPI().getPlayerManager();
        playerManager.sendPacket(player, spawnPacket);
        playerManager.sendPacket(player, metadataPacket);
    }

    /**
     * Resolves the global palette block-state id for a Bukkit {@link BlockData}, for use as a
     * block-display's displayed block.
     */
    public static int blockStateId(BlockData blockData) {
        return SpigotConversionUtil.fromBukkitBlockData(blockData).getGlobalId();
    }

    /**
     * Sends a destroy entities packet to remove the given entity IDs from the player's client.
     */
    public static void destroy(Player player, int... entityIds) {
        if (entityIds.length == 0) return;
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityIds);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroyPacket);
    }
}
