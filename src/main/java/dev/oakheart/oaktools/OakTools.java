package dev.oakheart.oaktools;

import dev.oakheart.message.MessageManager;
import dev.oakheart.models.ModelProviderManager;
import dev.oakheart.oaktools.commands.OakToolsCommand;
import dev.oakheart.oaktools.config.ConfigManager;
import dev.oakheart.oaktools.integration.CoreProtectLogger;
import dev.oakheart.oaktools.integration.OverflowHook;
import dev.oakheart.oaktools.items.ItemFactory;
import dev.oakheart.oaktools.listeners.*;
import dev.oakheart.oaktools.managers.BreakingAnimationManager;
import dev.oakheart.oaktools.managers.PlacedBlockTracker;
import dev.oakheart.oaktools.managers.WandHistoryManager;
import dev.oakheart.oaktools.managers.WandPreviewManager;
import dev.oakheart.oaktools.recipes.RecipeManager;
import dev.oakheart.oaktools.services.*;
import dev.oakheart.util.DebugLogger;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class OakTools extends JavaPlugin {

    // Managers
    private ConfigManager configManager;
    private ModelProviderManager modelProviderManager;
    private RecipeManager recipeManager;

    // Factories
    private ItemFactory itemFactory;

    // Services
    private DurabilityService durabilityService;
    private DisplayService displayService;
    private MessageManager messageManager;
    private ProtectionService protectionService;
    private DebugLogger debugLogger;

    // Listeners (stored for reload callbacks)
    private RecipeDiscoveryListener recipeDiscoveryListener;

    // Wand managers
    private WandHistoryManager wandHistoryManager;
    private WandPreviewManager wandPreviewManager;

    // Harvesting tool managers
    private BreakingAnimationManager breakingAnimationManager;
    private PlacedBlockTracker placedBlockTracker;

    // Integration
    private CoreProtectLogger coreProtectLogger;
    private OverflowHook overflowHook;

    @Override
    public void onEnable() {
        try {
            initializeComponents();
            registerListeners();
            registerCommands();
            initializeMetrics();
            scheduleRecipeRegistration();
            startManagers();

            getLogger().info("OakTools enabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable OakTools", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (breakingAnimationManager != null) {
            breakingAnimationManager.stop();
        }
        if (wandPreviewManager != null) {
            wandPreviewManager.stop();
        }
        if (recipeManager != null) {
            recipeManager.unregisterRecipes();
        }
        getLogger().info("OakTools disabled.");
    }

    private void initializeComponents() {
        configManager = new ConfigManager(this);
        configManager.load();

        messageManager = new MessageManager(this, getLogger());
        messageManager.load();

        debugLogger = new DebugLogger(getLogger(), configManager::isDebug);

        modelProviderManager = new ModelProviderManager(getLogger());
        logModelConfiguration();
        recipeManager = new RecipeManager(this);

        itemFactory = new ItemFactory(this);

        durabilityService = new DurabilityService(this);
        displayService = new DisplayService(this);
        protectionService = new ProtectionService(this);

        wandHistoryManager = new WandHistoryManager(this);
        wandPreviewManager = new WandPreviewManager(this);

        // Harvesting tool infrastructure
        overflowHook = new OverflowHook(this);
        overflowHook.initialize();

        breakingAnimationManager = new BreakingAnimationManager(this, overflowHook);
        placedBlockTracker = new PlacedBlockTracker(protectionService);

        coreProtectLogger = new CoreProtectLogger(this);
        coreProtectLogger.initialize();

    }

    private void registerListeners() {
        var pluginManager = getServer().getPluginManager();

        pluginManager.registerEvents(new FileListener(this), this);
        pluginManager.registerEvents(new TrowelListener(this), this);
        pluginManager.registerEvents(new WandListener(this, wandHistoryManager), this);
        pluginManager.registerEvents(wandHistoryManager, this);
        pluginManager.registerEvents(wandPreviewManager, this);
        pluginManager.registerEvents(new AnvilListener(this), this);
        pluginManager.registerEvents(new CraftingListener(this), this);
        recipeDiscoveryListener = new RecipeDiscoveryListener(this);
        pluginManager.registerEvents(recipeDiscoveryListener, this);
        pluginManager.registerEvents(new MendingListener(this), this);

        // Harvesting tool listeners
        pluginManager.registerEvents(breakingAnimationManager, this);
        pluginManager.registerEvents(new ExcavatorListener(this, breakingAnimationManager), this);
        pluginManager.registerEvents(new VeinMinerListener(this, breakingAnimationManager), this);
        pluginManager.registerEvents(new LumberjackListener(this, breakingAnimationManager, placedBlockTracker), this);
        pluginManager.registerEvents(placedBlockTracker, this);
        pluginManager.registerEvents(new EnchantBlockListener(this), this);
        pluginManager.registerEvents(new SickleListener(this), this);
        pluginManager.registerEvents(new ItemDamageListener(this), this);
    }

    private void registerCommands() {
        new OakToolsCommand(this).register();
    }

    private void initializeMetrics() {
        if (!configManager.isMetricsEnabled()) {
            getLogger().info("Metrics are disabled in config.");
            return;
        }

        try {
            new Metrics(this, 27955);
        } catch (Exception e) {
            getLogger().warning("Failed to initialize metrics: " + e.getMessage());
        }
    }

    private void scheduleRecipeRegistration() {
        boolean usesExternalProvider = false;

        for (dev.oakheart.oaktools.model.ToolType toolType : dev.oakheart.oaktools.model.ToolType.values()) {
            String modelId = configManager.getConfig().getString(
                    "tools." + toolType.getConfigKey() + ".model-id", "").toLowerCase();
            if (modelId.startsWith("nexo:") || modelId.startsWith("itemsadder:")) {
                usesExternalProvider = true;
                break;
            }
        }

        if (usesExternalProvider) {
            getLogger().info("External model provider detected, delaying recipe registration...");
            getServer().getScheduler().runTaskLater(this, () -> {
                recipeManager.registerRecipes();
                getLogger().info("Recipes registered with external model provider.");
            }, 20L);
        } else {
            recipeManager.registerRecipes();
        }
    }

    private void startManagers() {
        if (wandPreviewManager != null) {
            wandPreviewManager.start();
        }
        if (breakingAnimationManager != null) {
            breakingAnimationManager.start();
        }
    }

    /**
     * Re-initialize components that depend on config values after a reload.
     * Called from OakToolsCommand after config reload.
     */
    public void refreshAfterReload() {
        messageManager.reload();
        logModelConfiguration();
        coreProtectLogger.initialize();
        overflowHook.initialize();
        if (recipeDiscoveryListener != null) {
            recipeDiscoveryListener.rebuildCache();
        }
        if (wandPreviewManager != null) {
            wandPreviewManager.restart();
        }
    }

    /**
     * Log a debug message if debug mode is enabled.
     */
    public void debug(String message) {
        debugLogger.log(message);
    }

    private void logModelConfiguration() {
        var config = configManager.getConfig();
        String fileModel = getModelIdAsString(config, "tools.file.model-id");
        String trowelModel = getModelIdAsString(config, "tools.trowel.model-id");
        String wandModel = getModelIdAsString(config, "tools.wand.model-id");
        getLogger().info("Model configuration:");
        getLogger().info("  File: " + fileModel + " (provider: " + modelProviderManager.getProviderName(fileModel) + ")");
        getLogger().info("  Trowel: " + trowelModel + " (provider: " + modelProviderManager.getProviderName(trowelModel) + ")");
        getLogger().info("  Wand: " + wandModel + " (provider: " + modelProviderManager.getProviderName(wandModel) + ")");
    }

    /**
     * Get model ID as string. Handles both int and string config values.
     */
    private String getModelIdAsString(dev.oakheart.config.ConfigManager config, String path) {
        // Library ConfigManager always returns strings, but integers may be stored as "1001"
        return config.getString(path, "1001");
    }

    // Getters

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ModelProviderManager getModelProviderManager() {
        return modelProviderManager;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    public DurabilityService getDurabilityService() {
        return durabilityService;
    }

    public DisplayService getDisplayService() {
        return displayService;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public ProtectionService getProtectionService() {
        return protectionService;
    }

    public CoreProtectLogger getCoreProtectLogger() {
        return coreProtectLogger;
    }

    public WandHistoryManager getWandHistoryManager() {
        return wandHistoryManager;
    }

    public WandPreviewManager getWandPreviewManager() {
        return wandPreviewManager;
    }

    public BreakingAnimationManager getBreakingAnimationManager() {
        return breakingAnimationManager;
    }

    public OverflowHook getOverflowHook() {
        return overflowHook;
    }


}
