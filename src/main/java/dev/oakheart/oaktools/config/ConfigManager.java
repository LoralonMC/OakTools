package dev.oakheart.oaktools.config;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;

/**
 * Manages plugin configuration loading, saving, and reloading.
 */
public class ConfigManager {

    private final OakTools plugin;
    private FileConfiguration config;

    public ConfigManager(OakTools plugin) {
        this.plugin = plugin;
    }

    /**
     * Load the configuration from disk. Creates default config if missing.
     */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Always check for and add missing config keys
        // This ensures the config stays up-to-date even if manually edited
        if (mergeConfigDefaults()) {
            plugin.getLogger().info("Added missing config options with default values.");
            // Reload after merge
            plugin.reloadConfig();
            this.config = plugin.getConfig();
        }

        // Validate configuration
        ConfigValidator.validate(config, plugin.getLogger());
    }

    /**
     * Merge missing keys from the default config into the existing config.
     * Preserves all existing user values while adding new default keys.
     *
     * @return true if merge was successful
     */
    private boolean mergeConfigDefaults() {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            FileConfiguration userConfig = YamlConfiguration.loadConfiguration(configFile);

            // Load default config from JAR
            InputStream defaultStream = plugin.getResource("config.yml");
            if (defaultStream == null) {
                plugin.getLogger().warning("Could not find default config in plugin JAR");
                return false;
            }

            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream)
            );

            // Merge: add missing keys from default, keep existing user values
            boolean modified = mergeSection(userConfig, defaultConfig, "");

            if (modified) {
                // Save the merged config
                userConfig.save(configFile);
                plugin.getLogger().info("Config merge completed. " +
                    "Your existing settings were preserved, and new options were added.");
                return true;
            }

            return false;

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to merge config defaults", e);
            return false;
        }
    }

    /**
     * Recursively merge configuration sections.
     *
     * @param target the target config to update
     * @param source the source config with default values
     * @param path the current path (for nested sections)
     * @return true if any keys were added
     */
    private boolean mergeSection(ConfigurationSection target, ConfigurationSection source, String path) {
        boolean modified = false;

        for (String key : source.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;

            if (source.isConfigurationSection(key)) {
                // Handle nested sections
                if (!target.isConfigurationSection(key)) {
                    // Section doesn't exist in user config, copy entire section
                    target.set(key, source.getConfigurationSection(key));
                    plugin.getLogger().info("  Added new section: " + fullPath);
                    modified = true;
                } else {
                    // Recursively merge nested sections
                    boolean sectionModified = mergeSection(
                        target.getConfigurationSection(key),
                        source.getConfigurationSection(key),
                        fullPath
                    );
                    modified = modified || sectionModified;
                }
            } else {
                // Handle simple values
                if (!target.contains(key)) {
                    target.set(key, source.get(key));
                    plugin.getLogger().info("  Added new key: " + fullPath + " = " + source.get(key));
                    modified = true;
                }
                // If key exists in user config, keep their value (don't overwrite)
            }
        }

        return modified;
    }

    /**
     * Reload the configuration from disk.
     *
     * @return true if reload was successful, false if config is invalid
     */
    public boolean reload() {
        try {
            plugin.reloadConfig();
            FileConfiguration newConfig = plugin.getConfig();

            // Validate before applying
            if (!ConfigValidator.validate(newConfig, plugin.getLogger())) {
                plugin.getLogger().warning("Config validation failed. Keeping old configuration.");
                return false;
            }

            this.config = newConfig;
            plugin.getLogger().info("Configuration reloaded successfully.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload configuration", e);
            return false;
        }
    }

    /**
     * Get the current configuration.
     *
     * @return the FileConfiguration
     */
    public FileConfiguration getConfig() {
        return config;
    }
}
