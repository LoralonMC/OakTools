package dev.oakheart.oaktools.model;

/**
 * Represents the placement mode for the Builder's Wand tool.
 */
public enum WandMode {
    FACE,
    LINE;

    /**
     * Get the next mode in the cycle.
     *
     * @return the next mode
     */
    public WandMode next() {
        return switch (this) {
            case FACE -> LINE;
            case LINE -> FACE;
        };
    }

    /**
     * Safely parse a WandMode from a string, with fallback.
     *
     * @param value the string value to parse
     * @return the WandMode, or FACE as fallback
     */
    public static WandMode fromString(String value) {
        if (value == null) {
            return FACE;
        }
        try {
            return WandMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FACE;
        }
    }

    /**
     * Get the display name of this mode.
     *
     * @return the display name (capitalized)
     */
    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
