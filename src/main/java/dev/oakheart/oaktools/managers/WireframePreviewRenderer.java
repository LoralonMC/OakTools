package dev.oakheart.oaktools.managers;

import dev.oakheart.oaktools.integration.PacketPreviewRenderer;
import dev.oakheart.oaktools.util.WireframeEdgeBuilder;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
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

    // Maps the native single-space text-display background to a [0,1] unit square. Calibration
    // borrowed from TWME's TextDisplayShapes (getTextDisplayUnitSquare): translate(0.4).scale(8,4,1).
    private static final Matrix4f TEXT_UNIT_SQUARE = new Matrix4f().translate(0.4f, 0f, 0f).scale(8f, 4f, 1f);

    /**
     * Spawns wireframe beams for the given placement blocks, visible only to {@code player}.
     *
     * @param argb          the line color (used directly by text beams; block beams use {@code blockMaterial})
     * @param blockMaterial the block for block-display beams (nearest concrete to the line color)
     * @param textBeams     render flat text-display ribbons instead of block-display beams
     * @return the spawned packet entity IDs (empty if there was nothing to draw)
     */
    public int[] spawn(Player player, List<Block> placements, int argb, Material blockMaterial,
                       float thickness, boolean textBeams) {
        if (placements.isEmpty()) {
            return NO_BEAMS;
        }

        List<WireframeEdgeBuilder.Beam> beams = WireframeEdgeBuilder.build(placements);
        if (beams.size() > MAX_BEAMS) {
            beams = WireframeEdgeBuilder.boundingBox(placements);
        }

        return textBeams
                ? spawnTextBeams(player, beams, argb, thickness)
                : spawnBlockBeams(player, beams, blockMaterial, thickness);
    }

    private int[] spawnBlockBeams(Player player, List<WireframeEdgeBuilder.Beam> beams,
                                  Material material, float thickness) {
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

    private int[] spawnTextBeams(Player player, List<WireframeEdgeBuilder.Beam> beams,
                                 int argb, float thickness) {
        List<Integer> ids = new ArrayList<>(beams.size() * 2);
        for (WireframeEdgeBuilder.Beam beam : beams) {
            float x0 = beam.x(), y0 = beam.y(), z0 = beam.z();
            float x1 = x0, y1 = y0, z1 = z0;
            switch (beam.axis()) {
                case X -> x1 += beam.length();
                case Y -> y1 += beam.length();
                case Z -> z1 += beam.length();
            }
            // Double-sided: render the ribbon from both directions so it is visible from either face.
            spawnTextLine(player, argb, thickness, x0, y0, z0, x1, y1, z1, ids);
            spawnTextLine(player, argb, thickness, x1, y1, z1, x0, y0, z0, ids);
        }
        int[] arr = new int[ids.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = ids.get(i);
        }
        return arr;
    }

    /**
     * Spawns a single flat text-display ribbon from {@code (x0,y0,z0)} to {@code (x1,y1,z1)}.
     *
     * <p>Pipeline ported from TWME's TextDisplayShapes: build a JOML matrix
     * {@code translate(p1)·rotate·scale(len,thickness,1)·translate(0,-0.5,0)·unitSquare}, then let
     * JOML extract the Display translation/scale/left-rotation (lines have no shear, so no
     * right-rotation is needed). The entity spawns at {@code (x0,y0,z0)} and the translation is made
     * relative to it to keep the values small.
     */
    private void spawnTextLine(Player player, int argb, float thickness,
                               float x0, float y0, float z0,
                               float x1, float y1, float z1, List<Integer> ids) {
        Vector3f p1 = new Vector3f(x0, y0, z0);
        Vector3f p2 = new Vector3f(x1, y1, z1);
        Vector3f direction = new Vector3f(p2).sub(p1);
        float length = direction.length();
        if (length < 0.001f) {
            return;
        }

        Vector3f up = Math.abs(direction.dot(new Vector3f(0, 1, 0)) / length) > 0.99f
                ? new Vector3f(1, 0, 0)
                : new Vector3f(0, 1, 0);
        Vector3f zAxis = new Vector3f(direction).cross(up).normalize();
        Vector3f xAxis = new Vector3f(direction).normalize();
        Vector3f yAxis = new Vector3f(zAxis).cross(xAxis).normalize();
        Quaternionf rotation = new Quaternionf().lookAlong(new Vector3f(zAxis).mul(-1f), yAxis).conjugate();

        Matrix4f matrix = new Matrix4f()
                .translate(p1)
                .rotate(rotation)
                .scale(length, thickness, 1f)
                .translate(0f, -0.5f, 0f)
                .mul(TEXT_UNIT_SQUARE);

        Vector3f translation = matrix.getTranslation(new Vector3f()).sub(x0, y0, z0);
        Vector3f scale = matrix.getScale(new Vector3f());
        Quaternionf left = matrix.getUnnormalizedRotation(new Quaternionf());

        int id = PacketPreviewRenderer.nextEntityId();
        PacketPreviewRenderer.spawnTextDisplay(player, id, UUID.randomUUID(),
                x0, y0, z0,
                translation.x, translation.y, translation.z,
                left.x, left.y, left.z, left.w,
                scale.x, scale.y, scale.z, argb);
        ids.add(id);
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
