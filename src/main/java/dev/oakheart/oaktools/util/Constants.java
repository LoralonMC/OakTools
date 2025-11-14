package dev.oakheart.oaktools.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central constants and PDC keys for OakTools.
 */
public class Constants {

    // Namespace
    public static final String NAMESPACE = "oaktools";

    // PDC Keys (initialized in init())
    public static NamespacedKey TOOL_TYPE;
    public static NamespacedKey DURABILITY;
    public static NamespacedKey MAX_DURABILITY;
    public static NamespacedKey FEED_SOURCE;

    /**
     * Initialize all NamespacedKeys. Must be called on plugin enable.
     *
     * @param plugin the plugin instance
     */
    public static void init(Plugin plugin) {
        TOOL_TYPE = new NamespacedKey(plugin, "tool_type");
        DURABILITY = new NamespacedKey(plugin, "dur");
        MAX_DURABILITY = new NamespacedKey(plugin, "max_dur");
        FEED_SOURCE = new NamespacedKey(plugin, "feed_source");
    }
}
