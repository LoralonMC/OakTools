package dev.oakheart.oaktools.model;

/**
 * Represents the type of OakTools tool.
 */
public enum ToolType {
    FILE("File"),
    TROWEL("Trowel"),
    WAND("Builder's Wand");

    private final String displayName;

    ToolType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Safely parse a ToolType from a string.
     *
     * @param value the string value to parse
     * @return the ToolType, or null if unknown/null
     */
    public static ToolType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ToolType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get the display name of this tool type.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }
}
