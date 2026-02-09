package dev.oakheart.oaktools.model;

/**
 * Represents the type of OakTools tool.
 */
public enum ToolType {
    FILE,
    TROWEL;

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
     * @return the display name (capitalized)
     */
    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
