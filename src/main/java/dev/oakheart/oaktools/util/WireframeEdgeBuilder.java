package dev.oakheart.oaktools.util;

import org.bukkit.Axis;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Computes a wireframe outline for a set of unit-cube blocks.
 *
 * <p>Each block contributes its 12 edges; shared edges between adjacent blocks are
 * de-duplicated, and colinear runs of unit edges are merged into single long beams.
 * The result is a compact set of axis-aligned beams (a flat 3x3 face fill collapses
 * to a tidy grid; a straight line collapses to a handful of rails) rather than one
 * outline per block.
 */
public final class WireframeEdgeBuilder {

    private WireframeEdgeBuilder() {
    }

    /**
     * An axis-aligned beam starting at the integer lattice corner {@code (x, y, z)},
     * running {@code length} blocks along {@code axis}.
     */
    public record Beam(int x, int y, int z, Axis axis, int length) {
    }

    private record UnitEdge(Axis axis, int x, int y, int z) {
    }

    /**
     * Builds the de-duplicated, colinear-merged wireframe beams for the given blocks.
     */
    public static List<Beam> build(Collection<Block> blocks) {
        Set<UnitEdge> edges = new HashSet<>();
        for (Block b : blocks) {
            int x = b.getX();
            int y = b.getY();
            int z = b.getZ();
            for (int j = 0; j <= 1; j++) {
                for (int k = 0; k <= 1; k++) {
                    edges.add(new UnitEdge(Axis.X, x, y + j, z + k));
                }
            }
            for (int i = 0; i <= 1; i++) {
                for (int k = 0; k <= 1; k++) {
                    edges.add(new UnitEdge(Axis.Y, x + i, y, z + k));
                }
            }
            for (int i = 0; i <= 1; i++) {
                for (int j = 0; j <= 1; j++) {
                    edges.add(new UnitEdge(Axis.Z, x + i, y + j, z));
                }
            }
        }
        return mergeColinear(edges);
    }

    /**
     * A simple 12-beam bounding-box outline of the given blocks — used as a fallback
     * when the full wireframe would spawn too many beams.
     */
    public static List<Beam> boundingBox(Collection<Block> blocks) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Block b : blocks) {
            minX = Math.min(minX, b.getX());
            minY = Math.min(minY, b.getY());
            minZ = Math.min(minZ, b.getZ());
            maxX = Math.max(maxX, b.getX());
            maxY = Math.max(maxY, b.getY());
            maxZ = Math.max(maxZ, b.getZ());
        }
        int x0 = minX, y0 = minY, z0 = minZ;
        int x1 = maxX + 1, y1 = maxY + 1, z1 = maxZ + 1;
        int lx = x1 - x0, ly = y1 - y0, lz = z1 - z0;

        List<Beam> beams = new ArrayList<>(12);
        for (int y : new int[]{y0, y1}) {
            for (int z : new int[]{z0, z1}) {
                beams.add(new Beam(x0, y, z, Axis.X, lx));
            }
        }
        for (int x : new int[]{x0, x1}) {
            for (int z : new int[]{z0, z1}) {
                beams.add(new Beam(x, y0, z, Axis.Y, ly));
            }
        }
        for (int x : new int[]{x0, x1}) {
            for (int y : new int[]{y0, y1}) {
                beams.add(new Beam(x, y, z0, Axis.Z, lz));
            }
        }
        return beams;
    }

    private static List<Beam> mergeColinear(Set<UnitEdge> edges) {
        // For each axis, group edges by their two fixed coordinates, then collect the
        // varying coordinate. Consecutive varying coords form one merged beam.
        Map<Axis, Map<Long, TreeSet<Integer>>> groups = new EnumMap<>(Axis.class);
        for (Axis a : Axis.values()) {
            groups.put(a, new HashMap<>());
        }

        for (UnitEdge e : edges) {
            long key = switch (e.axis()) {
                case X -> pack(e.y(), e.z());
                case Y -> pack(e.x(), e.z());
                case Z -> pack(e.x(), e.y());
            };
            int varying = switch (e.axis()) {
                case X -> e.x();
                case Y -> e.y();
                case Z -> e.z();
            };
            groups.get(e.axis()).computeIfAbsent(key, k -> new TreeSet<>()).add(varying);
        }

        List<Beam> beams = new ArrayList<>();
        for (Axis axis : Axis.values()) {
            for (Map.Entry<Long, TreeSet<Integer>> entry : groups.get(axis).entrySet()) {
                int fixedA = unpackA(entry.getKey());
                int fixedB = unpackB(entry.getKey());
                Integer runStart = null;
                Integer prev = null;
                for (int c : entry.getValue()) {
                    if (runStart == null) {
                        runStart = c;
                        prev = c;
                    } else if (c == prev + 1) {
                        prev = c;
                    } else {
                        beams.add(makeBeam(axis, fixedA, fixedB, runStart, prev - runStart + 1));
                        runStart = c;
                        prev = c;
                    }
                }
                if (runStart != null) {
                    beams.add(makeBeam(axis, fixedA, fixedB, runStart, prev - runStart + 1));
                }
            }
        }
        return beams;
    }

    private static Beam makeBeam(Axis axis, int fixedA, int fixedB, int start, int length) {
        return switch (axis) {
            case X -> new Beam(start, fixedA, fixedB, Axis.X, length);
            case Y -> new Beam(fixedA, start, fixedB, Axis.Y, length);
            case Z -> new Beam(fixedA, fixedB, start, Axis.Z, length);
        };
    }

    private static long pack(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    private static int unpackA(long key) {
        return (int) (key >> 32);
    }

    private static int unpackB(long key) {
        return (int) key;
    }
}
