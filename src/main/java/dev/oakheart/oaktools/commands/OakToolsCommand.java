package dev.oakheart.oaktools.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.FeedSource;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.Constants;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

@SuppressWarnings("UnstableApiUsage")
public class OakToolsCommand {

    private final OakTools plugin;

    public OakToolsCommand(OakTools plugin) {
        this.plugin = plugin;
    }

    public void register() {
        LifecycleEventManager<Plugin> manager = plugin.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(buildCommand(), "OakTools custom tool management", List.of("otools", "ot"));
        });
    }

    private LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("oaktools")
                .executes(ctx -> {
                    sendHelp(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("give")
                        .requires(src -> src.getSender().hasPermission("oaktools.give"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("tool", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String input = builder.getRemainingLowerCase();
                                            for (ToolType type : ToolType.values()) {
                                                if (type == ToolType.SICKLE) continue; // Sickles use tier format
                                                String name = type.name().toLowerCase();
                                                if (name.startsWith(input)) {
                                                    builder.suggest(name);
                                                }
                                            }
                                            // Add sickle tier suggestions
                                            for (String tier : plugin.getConfigManager().getSickleTiers()) {
                                                String name = "sickle_" + tier;
                                                if (name.startsWith(input)) {
                                                    builder.suggest(name);
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> handleGive(ctx, -1, false))
                                        .then(Commands.literal("silent")
                                                .executes(ctx -> handleGive(ctx, -1, true)))
                                        .then(Commands.argument("durability", IntegerArgumentType.integer(1))
                                                .executes(ctx -> handleGive(ctx, IntegerArgumentType.getInteger(ctx, "durability"), false))
                                                .then(Commands.literal("silent")
                                                        .executes(ctx -> handleGive(ctx, IntegerArgumentType.getInteger(ctx, "durability"), true)))))))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("oaktools.reload"))
                        .executes(ctx -> handleReload(ctx.getSource().getSender())))
                .then(Commands.literal("info")
                        .requires(src -> src.getSender().hasPermission("oaktools.info"))
                        .executes(ctx -> handleInfo(ctx.getSource().getSender(), null))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Player target = resolvePlayer(ctx);
                                    if (target == null) return Command.SINGLE_SUCCESS;
                                    return handleInfo(ctx.getSource().getSender(), target);
                                })))
                .then(Commands.literal("repair")
                        .requires(src -> src.getSender().hasPermission("oaktools.repair"))
                        .executes(ctx -> handleRepair(ctx.getSource().getSender(), null))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Player target = resolvePlayer(ctx);
                                    if (target == null) return Command.SINGLE_SUCCESS;
                                    return handleRepair(ctx.getSource().getSender(), target);
                                })))
                .build();
    }

    private Player resolvePlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> players = resolver.resolve(ctx.getSource());
            if (players.isEmpty()) return null;
            return players.getFirst();
        } catch (Exception e) {
            return null;
        }
    }

    // ===== Give =====

    private int handleGive(CommandContext<CommandSourceStack> ctx, int durability, boolean silent) {
        CommandSender sender = ctx.getSource().getSender();

        Player target = resolvePlayer(ctx);
        if (target == null) {
            plugin.getMessageManager().sendCommandMessage(sender, "give.player-not-found", Map.of("player", "unknown"));
            return Command.SINGLE_SUCCESS;
        }

        String toolArg = StringArgumentType.getString(ctx, "tool").toLowerCase();

        // Handle sickle_<tier> format
        if (toolArg.startsWith("sickle_")) {
            String tier = toolArg.substring("sickle_".length());
            if (!plugin.getConfigManager().getSickleTiers().contains(tier)) {
                plugin.getMessageManager().sendCommandMessage(sender, "give.invalid-tool");
                return Command.SINGLE_SUCCESS;
            }

            ItemStack tool = plugin.getItemFactory().createSickle(tier);
            target.getInventory().addItem(tool);

            String displayName = plugin.getConfigManager().getSickleDisplayName(tier);
            // Strip MiniMessage tags for plain text in command feedback
            String plainName = displayName.replaceAll("<[^>]+>", "");
            plugin.getMessageManager().sendCommandMessage(sender, "give.success-sender",
                    Map.of("tool", plainName, "player", target.getName()));
            if (!silent) {
                plugin.getMessageManager().sendCommandMessage(target, "give.success-target",
                        Map.of("tool", plainName));
            }
            return Command.SINGLE_SUCCESS;
        }

        ToolType toolType;
        try {
            toolType = ToolType.valueOf(toolArg.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getMessageManager().sendCommandMessage(sender, "give.invalid-tool");
            return Command.SINGLE_SUCCESS;
        }

        int maxDurability = plugin.getConfigManager().getMaxDurability(toolType);

        // -1 = full durability (default when no argument given)
        if (durability == -1) {
            durability = maxDurability;
        }

        if (durability < 1 || durability > maxDurability) {
            plugin.getMessageManager().sendCommandMessage(sender, "give.invalid-durability",
                    Map.of("value", String.valueOf(durability)));
            return Command.SINGLE_SUCCESS;
        }

        int damage = maxDurability - durability;
        ItemStack tool = plugin.getItemFactory().createTool(toolType, damage);
        target.getInventory().addItem(tool);

        plugin.getMessageManager().sendCommandMessage(sender, "give.success-sender",
                Map.of("tool", toolType.getDisplayName(), "player", target.getName()));
        if (!silent) {
            plugin.getMessageManager().sendCommandMessage(target, "give.success-target",
                    Map.of("tool", toolType.getDisplayName()));
        }

        return Command.SINGLE_SUCCESS;
    }

    // ===== Reload =====

    private int handleReload(CommandSender sender) {
        plugin.getMessageManager().sendCommandMessage(sender, "reload.reloading");

        try {
            boolean success = plugin.getConfigManager().reload();

            if (!success) {
                plugin.getMessageManager().sendCommandMessage(sender, "reload.failed");
                return Command.SINGLE_SUCCESS;
            }

            plugin.getRecipeManager().unregisterRecipes();
            plugin.getRecipeManager().registerRecipes();
            plugin.refreshAfterReload();

            plugin.getMessageManager().sendCommandMessage(sender, "reload.success");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during reload", e);
            plugin.getMessageManager().sendCommandMessage(sender, "reload.failed");
        }

        return Command.SINGLE_SUCCESS;
    }

    // ===== Info =====

    private int handleInfo(CommandSender sender, Player target) {
        if (target == null) {
            if (!(sender instanceof Player player)) {
                plugin.getMessageManager().sendCommandMessage(sender, "info.must-specify-player");
                return Command.SINGLE_SUCCESS;
            }
            target = player;
        }

        ItemStack item = target.getInventory().getItemInMainHand();

        if (!plugin.getItemFactory().isTool(item)) {
            plugin.getMessageManager().sendCommandMessage(sender, "info.not-holding-tool",
                    Map.of("player", target.getName()));
            return Command.SINGLE_SUCCESS;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        int currentDamage = plugin.getDurabilityService().getCurrentDamage(item);
        int maxDurability = plugin.getDurabilityService().getMaxDurability(item);
        int remaining = maxDurability - currentDamage;

        plugin.getMessageManager().sendCommandMessage(sender, "info.header");
        plugin.getMessageManager().sendCommandMessage(sender, "info.player",
                Map.of("player", target.getName()));
        plugin.getMessageManager().sendCommandMessage(sender, "info.tool-type",
                Map.of("tool", toolType != null ? toolType.getDisplayName() : "Unknown"));
        plugin.getMessageManager().sendCommandMessage(sender, "info.durability",
                Map.of("remaining", String.valueOf(remaining), "max", String.valueOf(maxDurability)));

        if (toolType == ToolType.TROWEL) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String feedSourceString = meta.getPersistentDataContainer()
                        .get(Constants.FEED_SOURCE, PersistentDataType.STRING);
                FeedSource feedSource = FeedSource.fromString(feedSourceString);
                plugin.getMessageManager().sendCommandMessage(sender, "info.feed-source",
                        Map.of("feed_source", feedSource.getDisplayName()));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    // ===== Repair =====

    private int handleRepair(CommandSender sender, Player target) {
        if (target == null) {
            if (!(sender instanceof Player player)) {
                plugin.getMessageManager().sendCommandMessage(sender, "repair.must-specify-player");
                return Command.SINGLE_SUCCESS;
            }
            target = player;
        }

        ItemStack item = target.getInventory().getItemInMainHand();

        if (!plugin.getItemFactory().isTool(item)) {
            plugin.getMessageManager().sendCommandMessage(sender, "repair.not-holding-tool",
                    Map.of("player", target.getName()));
            return Command.SINGLE_SUCCESS;
        }

        plugin.getDurabilityService().repairFully(item);
        plugin.getDisplayService().updateDisplay(item);

        plugin.getMessageManager().sendCommandMessage(sender, "repair.success-sender",
                Map.of("player", target.getName()));
        if (target != sender) {
            plugin.getMessageManager().sendCommandMessage(target, "repair.success-target");
        }

        return Command.SINGLE_SUCCESS;
    }

    // ===== Help =====

    private void sendHelp(CommandSender sender) {
        plugin.getMessageManager().sendCommandMessage(sender, "help.header");
        if (sender.hasPermission("oaktools.give")) {
            plugin.getMessageManager().sendCommandMessage(sender, "help.give");
        }
        if (sender.hasPermission("oaktools.reload")) {
            plugin.getMessageManager().sendCommandMessage(sender, "help.reload");
        }
        if (sender.hasPermission("oaktools.info")) {
            plugin.getMessageManager().sendCommandMessage(sender, "help.info");
        }
        if (sender.hasPermission("oaktools.repair")) {
            plugin.getMessageManager().sendCommandMessage(sender, "help.repair");
        }
    }
}
