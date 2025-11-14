package dev.oakheart.oaktools.model;

/**
 * Represents the type of OakTools tool.
 */
public enum ToolType {
    FILE,
    TROWEL;

    /**
     * Safely parse a ToolType from a string, with fallback.
     *
     * @param value the string value to parse
     * @return the ToolType, or FILE as fallback
     */
    public static ToolType fromString(String value) {
        if (value == null) {
            return FILE;
        }
        try {
            return ToolType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FILE;
        }
    }

    /**
     * Get the display name of this tool type.
     *
     * @return the display name (capitalized)
     */
    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
