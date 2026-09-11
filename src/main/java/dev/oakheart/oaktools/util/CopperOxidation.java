package dev.oakheart.oaktools.util;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps each copper block to the next stage of its weathering.
 *
 * <p>Built at class-load from the Material names rather than a hand-written
 * table, because every copper family follows one convention: the stages are the
 * base name with an {@code EXPOSED_} / {@code WEATHERED_} / {@code OXIDIZED_}
 * prefix. Deriving it means copper blocks added by a future Minecraft version
 * are picked up with no code change, and there is no list to fall out of date.
 *
 * <p>Two things the convention does not cover, both handled explicitly:
 * <ul>
 *   <li>The plain block is {@code COPPER_BLOCK}, but its stages are
 *       {@code EXPOSED_COPPER} and friends — no {@code COPPER_BLOCK} prefix.</li>
 *   <li>{@code WAXED_} blocks are deliberately absent. Waxing is how a player
 *       locks a stage; advancing one would defeat the only tool they have for
 *       saying "leave this alone".</li>
 * </ul>
 */
public final class CopperOxidation {

    private static final String[] STAGES = {"EXPOSED_", "WEATHERED_", "OXIDIZED_"};

    private static final Map<Material, Material> NEXT = new EnumMap<>(Material.class);

    static {
        for (Material material : Material.values()) {
            if (material.isLegacy() || !material.isBlock()) continue;
            String name = material.name();
            if (!name.contains("COPPER") || name.startsWith("WAXED_")) continue;

            String base = stripStage(name);
            int stage = stageOf(name);
            if (stage >= STAGES.length) continue;      // already fully oxidised

            Material next = Material.getMaterial(STAGES[stage] + base);
            if (next != null && next.isBlock()) {
                NEXT.put(material, next);
            }
        }

        // The odd one out: COPPER_BLOCK weathers into EXPOSED_COPPER, not
        // EXPOSED_COPPER_BLOCK, so the convention above cannot reach it.
        Material exposed = Material.getMaterial("EXPOSED_COPPER");
        if (exposed != null) {
            NEXT.put(Material.COPPER_BLOCK, exposed);
        }
    }

    private CopperOxidation() {}

    /**
     * The next weathering stage, or null when there is none: not copper, already
     * fully oxidised, or waxed.
     */
    public static Material next(Material material) {
        return material == null ? null : NEXT.get(material);
    }

    /** Whether this block can be weathered any further. */
    public static boolean canWeather(Material material) {
        return next(material) != null;
    }

    /**
     * Whether this is copper that simply has nowhere left to go, which is worth
     * telling a player apart from "that is not copper at all".
     */
    public static boolean isFullyOxidised(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.contains("COPPER") && !name.startsWith("WAXED_")
                && name.startsWith("OXIDIZED_");
    }

    /** How many stages in this name already is: 0 for the base form. */
    private static int stageOf(String name) {
        for (int i = 0; i < STAGES.length; i++) {
            if (name.startsWith(STAGES[i])) return i + 1;
        }
        return 0;
    }

    private static String stripStage(String name) {
        for (String stage : STAGES) {
            if (name.startsWith(stage)) return name.substring(stage.length());
        }
        return name.toUpperCase(Locale.ROOT);
    }

    /** Exposed for the config validator and debug output. */
    public static int mappedCount() {
        return NEXT.size();
    }
}
