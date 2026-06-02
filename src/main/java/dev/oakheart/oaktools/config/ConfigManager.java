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
import org.bukkit.enchantments.Enchantment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
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
    private final Path configFile;
    private dev.oakheart.config.ConfigManager config;

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
    private NamedTextColor cachedWandPreviewLineColor = NamedTextColor.YELLOW;
    private float cachedWandPreviewLineThickness = 0.05f;

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
    private boolean cachedProtectExtendedPistons = true;

    // Harvesting tool settings
    private boolean cachedExcavatorEnabled = true;
    private boolean cachedLumberjackEnabled = false;
    private boolean cachedVeinMinerEnabled = false;
    private Map<ToolType, Integer> cachedMaxBlocks = new EnumMap<>(ToolType.class);
    private Map<ToolType, Integer> cachedBreakSpeedTicks = new EnumMap<>(ToolType.class);
    private Map<ToolType, Boolean> cachedUnbreakable = new EnumMap<>(ToolType.class);
    private Map<ToolType, Float> cachedMiningSpeed = new EnumMap<>(ToolType.class);
    private Map<ToolType, String> cachedHarvestLevel = new EnumMap<>(ToolType.class);
    private Map<ToolType, Boolean> cachedShowProgress = new EnumMap<>(ToolType.class);
    private int cachedExcavatorGridSize = 3;
    private int cachedLumberjackMinLeaves = 5;
    private boolean cachedVeinMinerGroupDeepslate = true;

    // Sickle settings
    private boolean cachedSickleEnabled = true;
    private Map<String, Integer> cachedSickleRadius = new HashMap<>();
    private Map<String, Material> cachedSickleBaseMaterial = new HashMap<>();
    private Map<String, String> cachedSickleDisplayName = new HashMap<>();
    private Map<String, String> cachedSickleModelId = new HashMap<>();
    private Set<String> cachedSickleTiers = new HashSet<>();
    private Set<Material> cachedSickleClearableVegetation = EnumSet.noneOf(Material.class);

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
        this.configFile = plugin.getDataFolder().toPath().resolve("config.yml");
    }

    public void load() {
        if (!configFile.toFile().exists()) {
            plugin.saveResource("config.yml", false);
        }

        try {
            config = dev.oakheart.config.ConfigManager.load(configFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }

        mergeDefaults();

        if (!ConfigValidator.validate(config, logger)) {
            logger.warning("Configuration has fatal errors — some features may not work correctly.");
        }

        cacheValues();
    }

    public boolean reload() {
        try {
            config.reload();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to reload config.yml", e);
            return false;
        }

        mergeDefaults();

        if (!ConfigValidator.validate(config, logger)) {
            logger.warning("Config validation failed. Keeping old cached values.");
            return false;
        }

        cacheValues();
        logger.info("Configuration reloaded successfully.");
        return true;
    }

    private void mergeDefaults() {
        try (var stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                logger.warning("Could not load default config from JAR");
                return;
            }
            var defaults = dev.oakheart.config.ConfigManager.fromStream(stream);
            if (config.mergeDefaults(defaults)) {
                config.save();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to merge default config", e);
        }
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
        cachedWandPreviewLineColor = parseNamedColor(
                config.getString("tools.wand.preview.line-color", "YELLOW"),
                NamedTextColor.YELLOW);
        cachedWandPreviewLineThickness = (float) config.getDouble("tools.wand.preview.line-thickness", 0.05);
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
        cachedProtectExtendedPistons = config.getBoolean("tools.file.protect-extended-pistons", true);

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
        Map<ToolType, Float> miningSpeedMap = new EnumMap<>(ToolType.class);
        Map<ToolType, String> harvestLevelMap = new EnumMap<>(ToolType.class);
        Map<ToolType, Boolean> showProgressMap = new EnumMap<>(ToolType.class);
        for (ToolType toolType : List.of(ToolType.EXCAVATOR, ToolType.LUMBERJACK, ToolType.VEIN_MINER)) {
            String key = toolType.getConfigKey();
            maxBlocksMap.put(toolType, config.getInt("tools." + key + ".max-blocks", 64));
            breakSpeedMap.put(toolType, config.getInt("tools." + key + ".break-speed-ticks", 1));
            unbreakableMap.put(toolType, config.getBoolean("tools." + key + ".durability.unbreakable", false));
            miningSpeedMap.put(toolType, (float) config.getDouble("tools." + key + ".mining-speed", 0));
            harvestLevelMap.put(toolType, config.getString("tools." + key + ".harvest-level", "none"));
            showProgressMap.put(toolType, config.getBoolean("tools." + key + ".show-progress", true));
        }
        cachedMaxBlocks = maxBlocksMap;
        cachedBreakSpeedTicks = breakSpeedMap;
        cachedUnbreakable = unbreakableMap;
        cachedMiningSpeed = miningSpeedMap;
        cachedHarvestLevel = harvestLevelMap;
        cachedShowProgress = showProgressMap;

        // Excavator-specific
        int gridSize = config.getInt("tools.excavator.grid-size", 3);
        if (gridSize % 2 == 0) gridSize++; // Force odd
        cachedExcavatorGridSize = gridSize;

        // Sickle tiers
        cachedSickleEnabled = config.getBoolean("tools.sickle.enabled", true);
        Map<String, Integer> sickleRadii = new HashMap<>();
        Map<String, Material> sickleMats = new HashMap<>();
        Map<String, String> sickleNames = new HashMap<>();
        Map<String, String> sickleModels = new HashMap<>();
        Set<String> sickleTierSet = new HashSet<>();
        var tiersSection = config.getSection("tools.sickle.tiers");
        if (tiersSection != null) {
            for (String tier : tiersSection.getKeys(false)) {
                sickleTierSet.add(tier);
                sickleRadii.put(tier, tiersSection.getInt(tier + ".radius", 0));
                sickleNames.put(tier, tiersSection.getString(tier + ".display-name",
                        "<white>" + capitalize(tier) + " Sickle</white>"));
                sickleModels.put(tier, tiersSection.getString(tier + ".model-id", ""));

                // Derive base material from tier name if not explicitly set
                Material baseMat = deriveSickleBaseMaterial(tier);
                sickleMats.put(tier, baseMat);
            }
        }
        cachedSickleRadius = sickleRadii;
        cachedSickleBaseMaterial = sickleMats;
        cachedSickleDisplayName = sickleNames;
        cachedSickleModelId = sickleModels;
        cachedSickleTiers = sickleTierSet;

        Set<Material> clearableVeg = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("tools.sickle.clearable-vegetation")) {
            try {
                clearableVeg.add(Material.valueOf(name));
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid clearable-vegetation material: " + name);
            }
        }
        cachedSickleClearableVegetation = clearableVeg;

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

            // Repair material (NONE = no anvil repair)
            String materialName = config.getString("tools." + toolLower + ".durability.repair-material", "IRON_INGOT");
            if (!materialName.equalsIgnoreCase("NONE") && !materialName.isEmpty()) {
                try {
                    repairMats.put(toolType, Material.valueOf(materialName));
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid repair material for " + toolType + ": " + materialName);
                }
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

    public dev.oakheart.config.ConfigManager getConfig() {
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

    public boolean isProtectExtendedPistons() {
        return cachedProtectExtendedPistons;
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

    public NamedTextColor getWandPreviewLineColor() {
        return cachedWandPreviewLineColor;
    }

    public float getWandPreviewLineThickness() {
        return cachedWandPreviewLineThickness;
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

    public float getMiningSpeed(ToolType toolType) {
        return cachedMiningSpeed.getOrDefault(toolType, 0f);
    }

    public String getHarvestLevel(ToolType toolType) {
        return cachedHarvestLevel.getOrDefault(toolType, "none");
    }

    public boolean isShowProgress(ToolType toolType) {
        return cachedShowProgress.getOrDefault(toolType, true);
    }

    public int getExcavatorGridSize() {
        return cachedExcavatorGridSize;
    }

    public Set<Material> getSickleClearableVegetation() {
        return cachedSickleClearableVegetation;
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

    // Sickle getters

    public boolean isSickleEnabled() {
        return cachedSickleEnabled;
    }

    public Set<String> getSickleTiers() {
        return cachedSickleTiers;
    }

    public int getSickleRadius(String tier) {
        return cachedSickleRadius.getOrDefault(tier, 0);
    }

    public Material getSickleBaseMaterial(String tier) {
        return cachedSickleBaseMaterial.getOrDefault(tier, Material.WOODEN_HOE);
    }

    public String getSickleDisplayName(String tier) {
        return cachedSickleDisplayName.getOrDefault(tier, "<white>Sickle</white>");
    }

    public String getSickleModelId(String tier) {
        return cachedSickleModelId.getOrDefault(tier, "");
    }

    private static Material deriveSickleBaseMaterial(String tier) {
        return switch (tier.toLowerCase()) {
            case "wooden" -> Material.WOODEN_HOE;
            case "stone" -> Material.STONE_HOE;
            case "iron" -> Material.IRON_HOE;
            case "gold" -> Material.GOLDEN_HOE;
            case "diamond" -> Material.DIAMOND_HOE;
            case "netherite" -> Material.NETHERITE_HOE;
            default -> Material.WOODEN_HOE;
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
