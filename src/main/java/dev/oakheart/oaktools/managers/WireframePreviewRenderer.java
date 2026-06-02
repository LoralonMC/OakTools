package dev.oakheart.oaktools.managers;

import dev.oakheart.oaktools.integration.PacketPreviewRenderer;
import dev.oakheart.oaktools.util.WireframeEdgeBuilder;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders a per-cell wireframe preview as thin coloured block-display "beams" — one per merged
 * edge of the placement set (see {@link WireframeEdgeBuilder}).
 *
 * <p>Beams are sent as client-only packet entities via {@link PacketPreviewRenderer}, so they
 * require PacketEvents. Display entities have no hitbox, so the beams are non-collidable (you walk
 * straight through them) and non-interactable.
 */
public class WireframePreviewRenderer {

    /** Above this many beams, fall back to a simple bounding-box outline to avoid entity spam. */
    private static final int MAX_BEAMS = 256;

    private static final int[] NO_BEAMS = new int[0];

    /** The 16 named preview colours mapped to the nearest solid concrete block. */
    private static final Map<NamedTextColor, Material> COLOR_BLOCKS = Map.ofEntries(
            Map.entry(NamedTextColor.BLACK, Material.BLACK_CONCRETE),
            Map.entry(NamedTextColor.DARK_BLUE, Material.BLUE_CONCRETE),
            Map.entry(NamedTextColor.DARK_GREEN, Material.GREEN_CONCRETE),
            Map.entry(NamedTextColor.DARK_AQUA, Material.CYAN_CONCRETE),
            Map.entry(NamedTextColor.DARK_RED, Material.RED_CONCRETE),
            Map.entry(NamedTextColor.DARK_PURPLE, Material.PURPLE_CONCRETE),
            Map.entry(NamedTextColor.GOLD, Material.ORANGE_CONCRETE),
            Map.entry(NamedTextColor.GRAY, Material.LIGHT_GRAY_CONCRETE),
            Map.entry(NamedTextColor.DARK_GRAY, Material.GRAY_CONCRETE),
            Map.entry(NamedTextColor.BLUE, Material.LIGHT_BLUE_CONCRETE),
            Map.entry(NamedTextColor.GREEN, Material.LIME_CONCRETE),
            Map.entry(NamedTextColor.AQUA, Material.CYAN_CONCRETE),
            Map.entry(NamedTextColor.RED, Material.RED_CONCRETE),
            Map.entry(NamedTextColor.LIGHT_PURPLE, Material.MAGENTA_CONCRETE),
            Map.entry(NamedTextColor.YELLOW, Material.YELLOW_CONCRETE),
            Map.entry(NamedTextColor.WHITE, Material.WHITE_CONCRETE)
    );

    /**
     * Spawns wireframe beams for the given placement blocks, visible only to {@code player}.
     *
     * @return the spawned packet entity IDs (empty if there was nothing to draw)
     */
    public int[] spawn(Player player, List<Block> placements, NamedTextColor color, float thickness) {
        if (placements.isEmpty()) {
            return NO_BEAMS;
        }

        List<WireframeEdgeBuilder.Beam> beams = WireframeEdgeBuilder.build(placements);
        if (beams.size() > MAX_BEAMS) {
            beams = WireframeEdgeBuilder.boundingBox(placements);
        }

        Material material = COLOR_BLOCKS.getOrDefault(color, Material.YELLOW_CONCRETE);
        int blockStateId = PacketPreviewRenderer.blockStateId(material.createBlockData());

        int[] ids = new int[beams.size()];
        for (int i = 0; i < beams.size(); i++) {
            WireframeEdgeBuilder.Beam beam = beams.get(i);
            float[] t = beamTransform(beam.axis(), beam.length(), thickness);
            int id = PacketPreviewRenderer.nextEntityId();
            PacketPreviewRenderer.spawnBlockDisplay(player, id, UUID.randomUUID(),
                    beam.x(), beam.y(), beam.z(),
                    t[0], t[1], t[2], t[3], t[4], t[5], blockStateId);
            ids[i] = id;
        }
        return ids;
    }

    /**
     * Returns {@code {tx, ty, tz, sx, sy, sz}} — the translation and scale that turn the unit block
     * model into a thin beam of {@code length} blocks along {@code axis}, centered on the lattice edge.
     */
    private float[] beamTransform(Axis axis, int length, float thickness) {
        float half = thickness / 2f;
        return switch (axis) {
            case X -> new float[]{0, -half, -half, length, thickness, thickness};
            case Y -> new float[]{-half, 0, -half, thickness, length, thickness};
            case Z -> new float[]{-half, -half, 0, thickness, thickness, length};
        };
    }
}
