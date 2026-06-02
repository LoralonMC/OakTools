package dev.oakheart.oaktools.util;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

/**
 * Resolves a Builder's Wand preview color from config. A color may be a named color
 * (e.g. {@code yellow}) or a hex code ({@code #RRGGBB} or {@code #AARRGGBB} with alpha).
 *
 * <p>Text-display beams use the exact ARGB value (alpha included); block-display beams
 * snap to the nearest solid concrete block (alpha ignored).
 */
public final class PreviewColor {

    private PreviewColor() {
    }

    /** Default preview color (opaque Minecraft yellow). */
    public static final int DEFAULT_ARGB = 0xFF000000 | NamedTextColor.YELLOW.value();

    private record Swatch(Material material, int r, int g, int b) {
    }

    /** Approximate average RGB of each concrete block, for nearest-color matching. */
    private static final List<Swatch> CONCRETE = List.of(
            new Swatch(Material.WHITE_CONCRETE, 207, 213, 214),
            new Swatch(Material.ORANGE_CONCRETE, 224, 97, 0),
            new Swatch(Material.MAGENTA_CONCRETE, 169, 48, 159),
            new Swatch(Material.LIGHT_BLUE_CONCRETE, 35, 137, 198),
            new Swatch(Material.YELLOW_CONCRETE, 240, 175, 21),
            new Swatch(Material.LIME_CONCRETE, 94, 168, 24),
            new Swatch(Material.PINK_CONCRETE, 213, 101, 142),
            new Swatch(Material.GRAY_CONCRETE, 54, 57, 61),
            new Swatch(Material.LIGHT_GRAY_CONCRETE, 125, 125, 115),
            new Swatch(Material.CYAN_CONCRETE, 21, 119, 136),
            new Swatch(Material.PURPLE_CONCRETE, 100, 31, 156),
            new Swatch(Material.BLUE_CONCRETE, 44, 46, 143),
            new Swatch(Material.BROWN_CONCRETE, 96, 59, 31),
            new Swatch(Material.GREEN_CONCRETE, 73, 91, 35),
            new Swatch(Material.RED_CONCRETE, 142, 32, 32),
            new Swatch(Material.BLACK_CONCRETE, 8, 10, 15)
    );

    /**
     * Parses a config color string to an ARGB int, or returns {@code fallback} if invalid.
     * Accepts a named color or {@code #RRGGBB} / {@code #AARRGGBB}.
     */
    public static int parseArgb(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.trim();
        if (v.startsWith("#")) {
            String hex = v.substring(1);
            try {
                if (hex.length() == 6) {
                    return 0xFF000000 | Integer.parseInt(hex, 16);
                }
                if (hex.length() == 8) {
                    return (int) Long.parseLong(hex, 16);
                }
            } catch (NumberFormatException ignored) {
                // fall through to fallback
            }
            return fallback;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(v.toLowerCase());
        return named != null ? 0xFF000000 | named.value() : fallback;
    }

    /** Whether a config string is a valid color (named or hex). */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        if (v.startsWith("#")) {
            String hex = v.substring(1);
            if (hex.length() != 6 && hex.length() != 8) {
                return false;
            }
            try {
                Long.parseLong(hex, 16);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return NamedTextColor.NAMES.value(v.toLowerCase()) != null;
    }

    /** The concrete block nearest to the given ARGB color (alpha ignored). */
    public static Material nearestConcrete(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        Material best = Material.YELLOW_CONCRETE;
        long bestDist = Long.MAX_VALUE;
        for (Swatch s : CONCRETE) {
            long dr = r - s.r(), dg = g - s.g(), db = b - s.b();
            long dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = s.material();
            }
        }
        return best;
    }
}
