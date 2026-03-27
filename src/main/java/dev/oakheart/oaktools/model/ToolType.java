package dev.oakheart.oaktools.model;

/**
 * Represents the type of OakTools tool.
 */
public enum ToolType {
    FILE("File", "file"),
    TROWEL("Trowel", "trowel"),
    WAND("Builder's Wand", "wand"),
    EXCAVATOR("Excavation Shovel", "excavator"),
    LUMBERJACK("Lumberjack's Axe", "lumberjack"),
    VEIN_MINER("Vein Miner Pickaxe", "vein-miner");

    private final String displayName;
    private final String configKey;

    ToolType(String displayName, String configKey) {
        this.displayName = displayName;
        this.configKey = configKey;
    }

    /**
     * Safely parse a ToolType from a string.
     * Matches against enum name (e.g. "VEIN_MINER") or config key (e.g. "vein-miner").
     *
     * @param value the string value to parse
     * @return the ToolType, or null if unknown/null
     */
    public static ToolType fromString(String value) {
        if (value == null) {
            return null;
        }
        // Try enum name first
        try {
            return ToolType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }
        // Try config key
        for (ToolType type : values()) {
            if (type.configKey.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Get the display name of this tool type.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the config key used in config.yml paths (e.g. "vein-miner").
     */
    public String getConfigKey() {
        return configKey;
    }
}
