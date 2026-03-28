package dev.oakheart.oaktools.config;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.FeedSource;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.model.WandMode;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ConfigManager {

    private final OakTools plugin;
    private final Logger logger;
    private final File configFile;
    private FileConfiguration config;

    // Cached config values for hot paths
    private Set<Material> cachedReplaceableMaterials = EnumSet.noneOf(Material.class);
    private boolean cachedDebug = false;
    private boolean cachedMetricsEnabled = true;

    // Hot-path caching
    private int cachedDurabilityWarningThreshold = 20;
    private boolean cachedFileEnabled = true;
    private boolean cachedTrowelEnabled = true;
    private boolean cachedWandEnabled = true;
    private int cachedWandMaxBlocks = 64;
    private boolean cachedCreativeConsumeDurability = true;
    private boolean cachedAdventureConsumeDurability = true;
    private boolean cachedCreativeAllowUse = true;
    private boolean cachedAdventureAllowUse = false;
    private boolean cachedSpectatorAllowUse = false;
    private boolean cachedCreativeConsumeBlocks = false;
    private boolean cachedAdventureConsumeBlocks = true;
    private boolean cachedWandOffhandOverride = true;
    private boolean cachedWandUndoEnabled = true;
    private int cachedWandUndoMaxHistory = 5;
    private int cachedWandUndoExpireSeconds = 300;
    private boolean cachedWandPreviewEnabled = true;
    private int cachedWandPreviewIntervalTicks = 5;
    private NamedTextColor cachedWandPreviewGlowColor = NamedTextColor.YELLOW;

    // World restrictions
    private String cachedWorldsMode = "BLACKLIST";
    private Set<String> cachedWorldsList = Set.of();

    // File tool feature flags
    private boolean cachedFeatureMultipleFacing = true;
    private boolean cachedFeatureWalls = true;
    private boolean cachedFeatureStairs = true;
    private boolean cachedFeatureDirectional = true;
    private boolean cachedFeatureAxisRotation = true;
    private boolean cachedFeatureSlabs = true;

    // Harvesting tool settings
    private boolean cachedExcavatorEnabled = true;
    private boolean cachedLumberjackEnabled = false;
    private boolean cachedVeinMinerEnabled = false;
    private Map<ToolType, Integer> cachedMaxBlocks = new EnumMap<>(ToolType.class);
    private Map<ToolType, Integer> cachedBreakSpeedTicks = new EnumMap<>(ToolType.class);
    private Map<ToolType, Boolean> cachedUnbreakable = new EnumMap<>(ToolType.class);
    private int cachedLumberjackMinLeaves = 5;
    private boolean cachedVeinMinerGroupDeepslate = true;

    // CoreProtect integration
    private boolean cachedCoreProtectEnabled = true;
    private boolean cachedLogFileChanges = true;
    private boolean cachedLogTrowelPlacements = true;
    private boolean cachedLogWandPlacements = true;
    private boolean cachedLogHarvestingBreaks = true;

    // Configurable excluded blocks for File tool
    private Set<Material> cachedExcludedFileMaterials = EnumSet.noneOf(Material.class);

    // Per-tool config
    private Map<ToolType, Material> cachedRepairMaterials = new EnumMap<>(ToolType.class);
    private Map<ToolType, Integer> cachedRepairAmounts = new EnumMap<>(ToolType.class);
    private Map<ToolType, Integer> cachedMaxDurability = new EnumMap<>(ToolType.class);
    private Map<ToolType, Material> cachedBaseMaterials = new EnumMap<>(ToolType.class);
    private Map<ToolType, Boolean> cachedUseVanillaDamageBar = new EnumMap<>(ToolType.class);
    private Map<ToolType, Set<Enchantment>> cachedAllowedEnchantments = new EnumMap<>(ToolType.class);
    private Map<ToolType, Boolean> cachedDisplayEnabled = new EnumMap<>(ToolType.class);
    private Map<ToolType, String> cachedDisplayNameTemplates = new EnumMap<>(ToolType.class);
    private Map<ToolType, List<String>> cachedDisplayLoreTemplates = new EnumMap<>(ToolType.class);

    // Sound names
    private String cachedTrowelFeedSwitchSound = "ui.button.click";
    private String cachedWandModeSwitchSound = "ui.button.click";

    // Display names
    private Map<FeedSource, String> cachedFeedSourceNames = new EnumMap<>(FeedSource.class);
    private Map<WandMode, String> cachedWandModeNames = new EnumMap<>(WandMode.class);

    public ConfigManager(OakTools plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        mergeDefaults();

        if (!ConfigValidator.validate(config, logger)) {
            logger.warning("Configuration has fatal errors — some features may not work correctly.");
        }

        cacheValues();
    }

    public boolean reload() {
        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);
        applyDefaults(newConfig);

        if (!ConfigValidator.validate(newConfig, logger)) {
            logger.warning("Config validation failed. Keeping old configuration.");
            return false;
        }

        this.config = newConfig;
        cacheValues();
        logger.info("Configuration reloaded successfully.");
        return true;
    }

    private void mergeDefaults() {
        applyDefaults(config);
        saveNewKeys();
    }

    private void applyDefaults(FileConfiguration targetConfig) {
        try (var stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                logger.warning("Could not load default config from JAR");
                return;
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            targetConfig.setDefaults(defaults);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load default config", e);
        }
    }

    private void saveNewKeys() {
        try {
            FileConfiguration defaults = config.getDefaults() instanceof FileConfiguration fc ? fc : null;
            if (defaults != null && hasNewKeys(defaults)) {
                config.options().copyDefaults(true);
                config.save(configFile);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to save new config keys", e);
        }
    }

    private boolean hasNewKeys(FileConfiguration defaults) {
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!config.contains(key, true)) {
                return true;
            }
        }
        return false;
    }

    private void cacheValues() {
        cachedDebug = config.getBoolean("general.debug", false);
        cachedMetricsEnabled = config.getBoolean("metrics.enabled", true);

        Set<Material> materials = EnumSet.noneOf(Material.class);
        List<String> replaceableList = config.getStringList("tools.trowel.can-replace");
        for (String materialName : replaceableList) {
            try {
                materials.add(Material.valueOf(materialName));
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid replaceable material in config: " + materialName);
            }
        }
        cachedReplaceableMaterials = materials;

        // Hot-path values
        cachedDurabilityWarningThreshold = config.getInt("general.durability-warning-threshold", 10);
        cachedFileEnabled = config.getBoolean("tools.file.enabled", true);
        cachedTrowelEnabled = config.getBoolean("tools.trowel.enabled", true);
        cachedWandEnabled = config.getBoolean("tools.wand.enabled", true);
        cachedWandMaxBlocks = config.getInt("tools.wand.max-blocks", 64);
        cachedWandOffhandOverride = config.getBoolean("tools.wand.offhand-override", true);
        cachedWandUndoEnabled = config.getBoolean("tools.wand.undo.enabled", true);
        cachedWandUndoMaxHistory = config.getInt("tools.wand.undo.max-history", 5);
        cachedWandUndoExpireSeconds = config.getInt("tools.wand.undo.expire-seconds", 300);
        cachedWandPreviewEnabled = config.getBoolean("tools.wand.preview.enabled", true);
        cachedWandPreviewIntervalTicks = config.getInt("tools.wand.preview.interval-ticks", 5);
        cachedWandPreviewGlowColor = parseNamedColor(
                config.getString("tools.wand.preview.glow-color", "YELLOW"),
                NamedTextColor.YELLOW);
        cachedCreativeConsumeDurability = config.getBoolean("general.restrictions.gamemode.creative.consume-durability", true);
        cachedAdventureConsumeDurability = config.getBoolean("general.restrictions.gamemode.adventure.consume-durability", true);
        cachedCreativeAllowUse = config.getBoolean("general.restrictions.gamemode.creative.allow-use", true);
        cachedAdventureAllowUse = config.getBoolean("general.restrictions.gamemode.adventure.allow-use", false);
        cachedSpectatorAllowUse = config.getBoolean("general.restrictions.gamemode.spectator.allow-use", false);
        cachedCreativeConsumeBlocks = config.getBoolean("general.restrictions.gamemode.creative.consume-blocks", false);
        cachedAdventureConsumeBlocks = config.getBoolean("general.restrictions.gamemode.adventure.consume-blocks", true);

        // World restrictions
        cachedWorldsMode = config.getString("general.restrictions.worlds.mode", "BLACKLIST");
        cachedWorldsList = new HashSet<>(config.getStringList("general.restrictions.worlds.list"));

        // File tool feature flags
        cachedFeatureMultipleFacing = config.getBoolean("tools.file.features.multiple-facing", true);
        cachedFeatureWalls = config.getBoolean("tools.file.features.walls", true);
        cachedFeatureStairs = config.getBoolean("tools.file.features.stairs", true);
        cachedFeatureDirectional = config.getBoolean("tools.file.features.directional", true);
        cachedFeatureAxisRotation = config.getBoolean("tools.file.features.axis-rotation", true);
        cachedFeatureSlabs = config.getBoolean("tools.file.features.slabs", true);

        // CoreProtect integration
        cachedCoreProtectEnabled = config.getBoolean("integration.coreprotect.enabled", true);
        cachedLogFileChanges = config.getBoolean("integration.coreprotect.log-file-changes", true);
        cachedLogTrowelPlacements = config.getBoolean("integration.coreprotect.log-trowel-placements", true);
        cachedLogWandPlacements = config.getBoolean("integration.coreprotect.log-wand-placements", true);
        cachedLogHarvestingBreaks = config.getBoolean("integration.coreprotect.log-harvesting-breaks", true);

        // Harvesting tool settings
        cachedExcavatorEnabled = config.getBoolean("tools.excavator.enabled", true);
        cachedLumberjackEnabled = config.getBoolean("tools.lumberjack.enabled", false);
        cachedVeinMinerEnabled = config.getBoolean("tools.vein-miner.enabled", false);
        cachedLumberjackMinLeaves = config.getInt("tools.lumberjack.min-leaves", 5);
        cachedVeinMinerGroupDeepslate = config.getBoolean("tools.vein-miner.group-deepslate", true);

        Map<ToolType, Integer> maxBlocksMap = new EnumMap<>(ToolType.class);
        Map<ToolType, Integer> breakSpeedMap = new EnumMap<>(ToolType.class);
        Map<ToolType, Boolean> unbreakableMap = new EnumMap<>(ToolType.class);
        for (ToolType toolType : List.of(ToolType.EXCAVATOR, ToolType.LUMBERJACK, ToolType.VEIN_MINER)) {
            String key = toolType.getConfigKey();
            maxBlocksMap.put(toolType, config.getInt("tools." + key + ".max-blocks", 9));
            breakSpeedMap.put(toolType, config.getInt("tools." + key + ".break-speed-ticks", 1));
            unbreakableMap.put(toolType, config.getBoolean("tools." + key + ".durability.unbreakable", false));
        }
        cachedMaxBlocks = maxBlocksMap;
        cachedBreakSpeedTicks = breakSpeedMap;
        cachedUnbreakable = unbreakableMap;

        // Excluded file materials
        Set<Material> excluded = EnumSet.noneOf(Material.class);
        List<String> excludedList = config.getStringList("tools.file.excluded-blocks");
        for (String materialName : excludedList) {
            try {
                excluded.add(Material.valueOf(materialName));
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid excluded block material in config: " + materialName);
            }
        }
        cachedExcludedFileMaterials = excluded;

        // Per-tool config
        Map<ToolType, Material> repairMats = new EnumMap<>(ToolType.class);
        Map<ToolType, Integer> repairAmts = new EnumMap<>(ToolType.class);
        Map<ToolType, Integer> maxDurs = new EnumMap<>(ToolType.class);
        Map<ToolType, Material> baseMats = new EnumMap<>(ToolType.class);
        Map<ToolType, Boolean> vanillaBars = new EnumMap<>(ToolType.class);
        Map<ToolType, Set<Enchantment>> allowedEnchants = new EnumMap<>(ToolType.class);
        Map<ToolType, Boolean> displayEnabled = new EnumMap<>(ToolType.class);
        Map<ToolType, String> displayNames = new EnumMap<>(ToolType.class);
        Map<ToolType, List<String>> displayLore = new EnumMap<>(ToolType.class);

        for (ToolType toolType : ToolType.values()) {
            String toolLower = toolType.getConfigKey();

            // Repair material
            String materialName = config.getString("tools." + toolLower + ".durability.repair-material", "IRON_INGOT");
            try {
                repairMats.put(toolType, Material.valueOf(materialName));
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid repair material for " + toolType + ": " + materialName);
            }

            // Repair amount
            repairAmts.put(toolType, config.getInt("tools." + toolLower + ".durability.repair-amount", 63));

            // Max durability
            maxDurs.put(toolType, config.getInt("tools." + toolLower + ".durability.max", 250));

            // Base material
            String baseMaterialName = config.getString("tools." + toolLower + ".base-material", "WARPED_FUNGUS_ON_A_STICK");
            try {
                baseMats.put(toolType, Material.valueOf(baseMaterialName));
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid base-material '" + baseMaterialName + "' for " + toolType + ", using WARPED_FUNGUS_ON_A_STICK");
                baseMats.put(toolType, Material.WARPED_FUNGUS_ON_A_STICK);
            }

            // Use vanilla damage bar
            vanillaBars.put(toolType, config.getBoolean("tools." + toolLower + ".durability.use-vanilla-damage-bar", true));

            // Allowed enchantments
            List<String> enchantNames = config.getStringList("tools." + toolLower + ".allowed-enchantments");
            Set<Enchantment> enchants = enchantNames.stream()
                    .map(name -> RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                            .get(NamespacedKey.minecraft(name.toLowerCase())))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            allowedEnchants.put(toolType, enchants);

            // Display config
            displayEnabled.put(toolType, config.getBoolean("tools." + toolLower + ".display.enabled", true));
            displayNames.put(toolType, config.getString("tools." + toolLower + ".display.name",
                    "<white>" + toolType.getDisplayName() + "</white>"));
            displayLore.put(toolType, config.getStringList("tools." + toolLower + ".display.lore"));
        }

        cachedRepairMaterials = repairMats;
        cachedRepairAmounts = repairAmts;
        cachedMaxDurability = maxDurs;
        cachedBaseMaterials = baseMats;
        cachedUseVanillaDamageBar = vanillaBars;
        cachedAllowedEnchantments = allowedEnchants;
        cachedDisplayEnabled = displayEnabled;
        cachedDisplayNameTemplates = displayNames;
        cachedDisplayLoreTemplates = displayLore;

        // Sound names
        cachedTrowelFeedSwitchSound = config.getString("tools.trowel.sounds.feed-source-switch", "ui.button.click");
        cachedWandModeSwitchSound = config.getString("tools.wand.sounds.mode-switch", "ui.button.click");

        // Feed source display names
        Map<FeedSource, String> feedNames = new EnumMap<>(FeedSource.class);
        for (FeedSource fs : FeedSource.values()) {
            String key = switch (fs) {
                case HOTBAR -> "hotbar";
                case ROW_1 -> "row-1";
                case ROW_2 -> "row-2";
                case ROW_3 -> "row-3";
            };
            feedNames.put(fs, config.getString("tools.trowel.feed-source-names." + key, fs.getDisplayName()));
        }
        cachedFeedSourceNames = feedNames;

        // Wand mode display names
        Map<WandMode, String> modeNames = new EnumMap<>(WandMode.class);
        for (WandMode wm : WandMode.values()) {
            modeNames.put(wm, config.getString("tools.wand.mode-names." + wm.name().toLowerCase(), wm.getDisplayName()));
        }
        cachedWandModeNames = modeNames;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public Set<Material> getReplaceableMaterials() {
        return cachedReplaceableMaterials;
    }

    public boolean isDebug() {
        return cachedDebug;
    }

    public boolean isMetricsEnabled() {
        return cachedMetricsEnabled;
    }

    public int getDurabilityWarningThreshold() {
        return cachedDurabilityWarningThreshold;
    }

    public boolean isFileEnabled() {
        return cachedFileEnabled;
    }

    public boolean isTrowelEnabled() {
        return cachedTrowelEnabled;
    }

    public boolean isWandEnabled() {
        return cachedWandEnabled;
    }

    public int getWandMaxBlocks() {
        return cachedWandMaxBlocks;
    }

    public boolean isCreativeConsumeDurability() {
        return cachedCreativeConsumeDurability;
    }

    public boolean isAdventureConsumeDurability() {
        return cachedAdventureConsumeDurability;
    }

    public boolean isCreativeConsumeBlocks() {
        return cachedCreativeConsumeBlocks;
    }

    public boolean isAdventureConsumeBlocks() {
        return cachedAdventureConsumeBlocks;
    }

    public boolean shouldConsumeBlocks(GameMode mode) {
        return switch (mode) {
            case CREATIVE -> cachedCreativeConsumeBlocks;
            case ADVENTURE -> cachedAdventureConsumeBlocks;
            default -> true;
        };
    }

    public boolean isWorldAllowed(String worldName) {
        if (cachedWorldsMode.equalsIgnoreCase("WHITELIST")) {
            return cachedWorldsList.contains(worldName);
        } else {
            return !cachedWorldsList.contains(worldName);
        }
    }

    public boolean isFeatureMultipleFacing() {
        return cachedFeatureMultipleFacing;
    }

    public boolean isFeatureWalls() {
        return cachedFeatureWalls;
    }

    public boolean isFeatureStairs() {
        return cachedFeatureStairs;
    }

    public boolean isFeatureDirectional() {
        return cachedFeatureDirectional;
    }

    public boolean isFeatureAxisRotation() {
        return cachedFeatureAxisRotation;
    }

    public boolean isFeatureSlabs() {
        return cachedFeatureSlabs;
    }

    public boolean isGamemodeAllowed(GameMode mode) {
        return switch (mode) {
            case CREATIVE -> cachedCreativeAllowUse;
            case ADVENTURE -> cachedAdventureAllowUse;
            case SPECTATOR -> cachedSpectatorAllowUse;
            default -> true;
        };
    }

    public Set<Material> getExcludedFileMaterials() {
        return cachedExcludedFileMaterials;
    }

    public Material getRepairMaterial(ToolType toolType) {
        return cachedRepairMaterials.get(toolType);
    }

    public boolean isCoreProtectEnabled() {
        return cachedCoreProtectEnabled;
    }

    public boolean isLogFileChanges() {
        return cachedLogFileChanges;
    }

    public boolean isLogTrowelPlacements() {
        return cachedLogTrowelPlacements;
    }

    public boolean isLogWandPlacements() {
        return cachedLogWandPlacements;
    }

    public int getRepairAmount(ToolType toolType) {
        return cachedRepairAmounts.getOrDefault(toolType, 63);
    }

    public int getMaxDurability(ToolType toolType) {
        return cachedMaxDurability.getOrDefault(toolType, 250);
    }

    public Material getBaseMaterial(ToolType toolType) {
        return cachedBaseMaterials.getOrDefault(toolType, Material.WARPED_FUNGUS_ON_A_STICK);
    }

    public boolean isUseVanillaDamageBar(ToolType toolType) {
        return cachedUseVanillaDamageBar.getOrDefault(toolType, true);
    }

    public Set<Enchantment> getAllowedEnchantments(ToolType toolType) {
        return cachedAllowedEnchantments.getOrDefault(toolType, Set.of());
    }

    public boolean isDisplayEnabled(ToolType toolType) {
        return cachedDisplayEnabled.getOrDefault(toolType, true);
    }

    public String getDisplayNameTemplate(ToolType toolType) {
        return cachedDisplayNameTemplates.getOrDefault(toolType, "<white>" + toolType.getDisplayName() + "</white>");
    }

    public List<String> getDisplayLoreTemplate(ToolType toolType) {
        return cachedDisplayLoreTemplates.getOrDefault(toolType, List.of());
    }

    public String getTrowelFeedSwitchSound() {
        return cachedTrowelFeedSwitchSound;
    }

    public String getWandModeSwitchSound() {
        return cachedWandModeSwitchSound;
    }

    public String getFeedSourceDisplayName(FeedSource feedSource) {
        return cachedFeedSourceNames.getOrDefault(feedSource, feedSource.getDisplayName());
    }

    public String getWandModeDisplayName(WandMode wandMode) {
        return cachedWandModeNames.getOrDefault(wandMode, wandMode.getDisplayName());
    }

    public boolean isWandOffhandOverride() {
        return cachedWandOffhandOverride;
    }

    public boolean isWandUndoEnabled() {
        return cachedWandUndoEnabled;
    }

    public int getWandUndoMaxHistory() {
        return cachedWandUndoMaxHistory;
    }

    public int getWandUndoExpireSeconds() {
        return cachedWandUndoExpireSeconds;
    }

    public boolean isWandPreviewEnabled() {
        return cachedWandPreviewEnabled;
    }

    public int getWandPreviewIntervalTicks() {
        return cachedWandPreviewIntervalTicks;
    }

    public NamedTextColor getWandPreviewGlowColor() {
        return cachedWandPreviewGlowColor;
    }

    // Harvesting tool getters

    public boolean isExcavatorEnabled() {
        return cachedExcavatorEnabled;
    }

    public boolean isLumberjackEnabled() {
        return cachedLumberjackEnabled;
    }

    public boolean isVeinMinerEnabled() {
        return cachedVeinMinerEnabled;
    }

    public boolean isHarvestToolEnabled(ToolType toolType) {
        return switch (toolType) {
            case EXCAVATOR -> cachedExcavatorEnabled;
            case LUMBERJACK -> cachedLumberjackEnabled;
            case VEIN_MINER -> cachedVeinMinerEnabled;
            default -> false;
        };
    }

    public int getMaxBlocks(ToolType toolType) {
        return cachedMaxBlocks.getOrDefault(toolType, 9);
    }

    public int getBreakSpeedTicks(ToolType toolType) {
        return cachedBreakSpeedTicks.getOrDefault(toolType, 1);
    }

    public boolean isUnbreakable(ToolType toolType) {
        return cachedUnbreakable.getOrDefault(toolType, false);
    }

    public int getLumberjackMinLeaves() {
        return cachedLumberjackMinLeaves;
    }

    public boolean isVeinMinerGroupDeepslate() {
        return cachedVeinMinerGroupDeepslate;
    }

    public boolean isLogHarvestingBreaks() {
        return cachedLogHarvestingBreaks;
    }

    private NamedTextColor parseNamedColor(String name, NamedTextColor fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        NamedTextColor color = NamedTextColor.NAMES.value(name.toLowerCase());
        if (color == null) {
            logger.warning("Invalid glow color '" + name + "' — using default. Valid: " +
                    String.join(", ", NamedTextColor.NAMES.keys()));
            return fallback;
        }
        return color;
    }

    public String getMessage(String key) {
        return config.getString("messages." + key + ".text", "");
    }

    public String getMessageDisplay(String key) {
        return config.getString("messages." + key + ".display", "chat");
    }
}
