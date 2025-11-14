package dev.oakheart.oaktools;

import dev.oakheart.oaktools.commands.OakToolsCommand;
import dev.oakheart.oaktools.config.ConfigManager;
import dev.oakheart.oaktools.integration.CoreProtectLogger;
import dev.oakheart.oaktools.integration.ModelProviderManager;
import dev.oakheart.oaktools.items.ItemFactory;
import dev.oakheart.oaktools.listeners.*;
import dev.oakheart.oaktools.recipes.RecipeManager;
import dev.oakheart.oaktools.services.*;
import dev.oakheart.oaktools.util.Constants;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

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
    private MessageService messageService;
    private ProtectionService protectionService;

    // Integration
    private CoreProtectLogger coreProtectLogger;

    @Override
    public void onEnable() {
        getLogger().info("Enabling OakTools...");

        // Initialize PDC keys
        Constants.init(this);

        // Initialize configuration
        this.configManager = new ConfigManager(this);
        configManager.load();

        // Initialize managers
        this.modelProviderManager = new ModelProviderManager(this);
        modelProviderManager.initialize();

        this.recipeManager = new RecipeManager(this);

        // Initialize factories
        this.itemFactory = new ItemFactory(this);

        // Initialize services
        this.durabilityService = new DurabilityService(this);
        this.displayService = new DisplayService(this);
        this.messageService = new MessageService(this);
        this.protectionService = new ProtectionService(this);

        // Initialize integration
        this.coreProtectLogger = new CoreProtectLogger(this);
        coreProtectLogger.initialize();

        // Initialize metrics (bStats)
        initializeMetrics();

        // Register listeners
        registerListeners();

        // Register commands
        registerCommands();

        // Register recipes (delayed if using external model providers)
        scheduleRecipeRegistration();

        getLogger().info("OakTools enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling OakTools...");

        // Unregister recipes
        if (recipeManager != null) {
            recipeManager.unregisterRecipes();
        }

        getLogger().info("OakTools disabled.");
    }

    /**
     * Schedule recipe registration based on model provider type.
     * If using external providers (Nexo/ItemsAdder), delay registration to ensure they're fully loaded.
     */
    private void scheduleRecipeRegistration() {
        boolean usesExternalProvider = false;

        // Check if any tool uses external model providers
        String fileModel = configManager.getConfig().getString("tools.file.model_id", "");
        String trowelModel = configManager.getConfig().getString("tools.trowel.model_id", "");

        if (fileModel.toLowerCase().startsWith("nexo:") || fileModel.toLowerCase().startsWith("itemsadder:") ||
            trowelModel.toLowerCase().startsWith("nexo:") || trowelModel.toLowerCase().startsWith("itemsadder:")) {
            usesExternalProvider = true;
        }

        if (usesExternalProvider) {
            // Delay recipe registration by 1 second (20 ticks) to allow external providers to load
            getLogger().info("External model provider detected, delaying recipe registration...");
            getServer().getScheduler().runTaskLater(this, () -> {
                recipeManager.registerRecipes();
                getLogger().info("Recipes registered with external model provider");
            }, 20L);
        } else {
            // Register immediately for vanilla CustomModelData
            recipeManager.registerRecipes();
        }
    }

    /**
     * Register all event listeners.
     */
    private void registerListeners() {
        var pluginManager = getServer().getPluginManager();

        pluginManager.registerEvents(new FileListener(this), this);
        pluginManager.registerEvents(new TrowelListener(this), this);
        pluginManager.registerEvents(new AnvilListener(this), this);
        pluginManager.registerEvents(new CraftingListener(this), this);
        pluginManager.registerEvents(new RecipeDiscoveryListener(this), this);

        getLogger().info("Registered listeners");
    }

    /**
     * Register all commands.
     */
    private void registerCommands() {
        OakToolsCommand commandExecutor = new OakToolsCommand(this);

        var command = getCommand("oaktools");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
            getLogger().info("Registered commands");
        } else {
            getLogger().warning("Failed to register /oaktools command!");
        }
    }

    /**
     * Initialize bStats metrics if enabled in config.
     */
    private void initializeMetrics() {
        if (!configManager.getConfig().getBoolean("metrics.enabled", true)) {
            getLogger().info("Metrics are disabled in config");
            return;
        }

        try {
            // Plugin ID from bstats.org
            int pluginId = 27955;

            Metrics metrics = new Metrics(this, pluginId);

            getLogger().info("Metrics initialized (bStats)");

        } catch (Exception e) {
            getLogger().warning("Failed to initialize metrics: " + e.getMessage());
        }
    }

    // Getters for managers, factories, and services

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

    public MessageService getMessageService() {
        return messageService;
    }

    public ProtectionService getProtectionService() {
        return protectionService;
    }

    public CoreProtectLogger getCoreProtectLogger() {
        return coreProtectLogger;
    }
}
