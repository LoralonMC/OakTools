package dev.oakheart.oaktools.model;

/**
 * Represents the feed source for the Trowel tool.
 * Determines which inventory rows are scanned for random block selection.
 */
public enum FeedSource {
    HOTBAR("Hotbar", 0, 9),
    ROW_1("Row 1", 9, 18),
    ROW_2("Row 2", 18, 27),
    ROW_3("Row 3", 27, 36);

    private final String displayName;
    private final int startSlot;
    private final int endSlot;

    FeedSource(String displayName, int startSlot, int endSlot) {
        this.displayName = displayName;
        this.startSlot = startSlot;
        this.endSlot = endSlot;
    }

    /**
     * Get the display name of this feed source.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the start slot (inclusive) of this feed source.
     *
     * @return the start slot index
     */
    public int getStartSlot() {
        return startSlot;
    }

    /**
     * Get the end slot (exclusive) of this feed source.
     *
     * @return the end slot index
     */
    public int getEndSlot() {
        return endSlot;
    }

    /**
     * Get the next feed source in the cycle.
     *
     * @return the next feed source
     */
    public FeedSource next() {
        return switch (this) {
            case HOTBAR -> ROW_1;
            case ROW_1 -> ROW_2;
            case ROW_2 -> ROW_3;
            case ROW_3 -> HOTBAR;
        };
    }

    /**
     * Safely parse a FeedSource from a string, with fallback.
     *
     * @param value the string value to parse
     * @return the FeedSource, or HOTBAR as fallback
     */
    public static FeedSource fromString(String value) {
        if (value == null) {
            return HOTBAR;
        }
        try {
            return FeedSource.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return HOTBAR;
        }
    }
}
