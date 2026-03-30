package dev.oakheart.oaktools.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.oakheart.command.CommandRegistrar;
import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.FeedSource;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.Constants;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.logging.Level;

@SuppressWarnings("UnstableApiUsage")
public class OakToolsCommand {

    private final OakTools plugin;

    public OakToolsCommand(OakTools plugin) {
        this.plugin = plugin;
    }

    public void register() {
        CommandRegistrar.register(plugin, buildCommand(), "OakTools custom tool management", List.of("otools", "ot"));
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
            plugin.getMessageManager().sendCommand(sender, "give.player-not-found",
                    Placeholder.unparsed("player", "unknown"));
            return Command.SINGLE_SUCCESS;
        }

        String toolArg = StringArgumentType.getString(ctx, "tool").toLowerCase();

        // Handle sickle_<tier> format
        if (toolArg.startsWith("sickle_")) {
            String tier = toolArg.substring("sickle_".length());
            if (!plugin.getConfigManager().getSickleTiers().contains(tier)) {
                plugin.getMessageManager().sendCommand(sender, "give.invalid-tool");
                return Command.SINGLE_SUCCESS;
            }

            ItemStack tool = plugin.getItemFactory().createSickle(tier);
            target.getInventory().addItem(tool);

            String displayName = plugin.getConfigManager().getSickleDisplayName(tier);
            // Strip MiniMessage tags for plain text in command feedback
            String plainName = displayName.replaceAll("<[^>]+>", "");
            plugin.getMessageManager().sendCommand(sender, "give.success-sender",
                    Placeholder.unparsed("tool", plainName),
                    Placeholder.unparsed("player", target.getName()));
            if (!silent) {
                plugin.getMessageManager().sendCommand(target, "give.success-target",
                        Placeholder.unparsed("tool", plainName));
            }
            return Command.SINGLE_SUCCESS;
        }

        ToolType toolType;
        try {
            toolType = ToolType.valueOf(toolArg.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getMessageManager().sendCommand(sender, "give.invalid-tool");
            return Command.SINGLE_SUCCESS;
        }

        int maxDurability = plugin.getConfigManager().getMaxDurability(toolType);

        // -1 = full durability (default when no argument given)
        if (durability == -1) {
            durability = maxDurability;
        }

        if (durability < 1 || durability > maxDurability) {
            plugin.getMessageManager().sendCommand(sender, "give.invalid-durability",
                    Placeholder.unparsed("value", String.valueOf(durability)));
            return Command.SINGLE_SUCCESS;
        }

        int damage = maxDurability - durability;
        ItemStack tool = plugin.getItemFactory().createTool(toolType, damage);
        target.getInventory().addItem(tool);

        plugin.getMessageManager().sendCommand(sender, "give.success-sender",
                Placeholder.unparsed("tool", toolType.getDisplayName()),
                Placeholder.unparsed("player", target.getName()));
        if (!silent) {
            plugin.getMessageManager().sendCommand(target, "give.success-target",
                    Placeholder.unparsed("tool", toolType.getDisplayName()));
        }

        return Command.SINGLE_SUCCESS;
    }

    // ===== Reload =====

    private int handleReload(CommandSender sender) {
        plugin.getMessageManager().sendCommand(sender, "reload.reloading");

        try {
            boolean success = plugin.getConfigManager().reload();

            if (!success) {
                plugin.getMessageManager().sendCommand(sender, "reload.failed");
                return Command.SINGLE_SUCCESS;
            }

            plugin.getRecipeManager().unregisterRecipes();
            plugin.getRecipeManager().registerRecipes();
            plugin.refreshAfterReload();

            plugin.getMessageManager().sendCommand(sender, "reload.success");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during reload", e);
            plugin.getMessageManager().sendCommand(sender, "reload.failed");
        }

        return Command.SINGLE_SUCCESS;
    }

    // ===== Info =====

    private int handleInfo(CommandSender sender, Player target) {
        if (target == null) {
            if (!(sender instanceof Player player)) {
                plugin.getMessageManager().sendCommand(sender, "info.must-specify-player");
                return Command.SINGLE_SUCCESS;
            }
            target = player;
        }

        ItemStack item = target.getInventory().getItemInMainHand();

        if (!plugin.getItemFactory().isTool(item)) {
            plugin.getMessageManager().sendCommand(sender, "info.not-holding-tool",
                    Placeholder.unparsed("player", target.getName()));
            return Command.SINGLE_SUCCESS;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(item);
        int currentDamage = plugin.getDurabilityService().getCurrentDamage(item);
        int maxDurability = plugin.getDurabilityService().getMaxDurability(item);
        int remaining = maxDurability - currentDamage;

        plugin.getMessageManager().sendCommand(sender, "info.header");
        plugin.getMessageManager().sendCommand(sender, "info.player",
                Placeholder.unparsed("player", target.getName()));
        plugin.getMessageManager().sendCommand(sender, "info.tool-type",
                Placeholder.unparsed("tool", toolType != null ? toolType.getDisplayName() : "Unknown"));
        plugin.getMessageManager().sendCommand(sender, "info.durability",
                Placeholder.unparsed("remaining", String.valueOf(remaining)),
                Placeholder.unparsed("max", String.valueOf(maxDurability)));

        if (toolType == ToolType.TROWEL) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String feedSourceString = meta.getPersistentDataContainer()
                        .get(Constants.FEED_SOURCE, PersistentDataType.STRING);
                FeedSource feedSource = FeedSource.fromString(feedSourceString);
                plugin.getMessageManager().sendCommand(sender, "info.feed-source",
                        Placeholder.unparsed("feed_source", feedSource.getDisplayName()));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    // ===== Repair =====

    private int handleRepair(CommandSender sender, Player target) {
        if (target == null) {
            if (!(sender instanceof Player player)) {
                plugin.getMessageManager().sendCommand(sender, "repair.must-specify-player");
                return Command.SINGLE_SUCCESS;
            }
            target = player;
        }

        ItemStack item = target.getInventory().getItemInMainHand();

        if (!plugin.getItemFactory().isTool(item)) {
            plugin.getMessageManager().sendCommand(sender, "repair.not-holding-tool",
                    Placeholder.unparsed("player", target.getName()));
            return Command.SINGLE_SUCCESS;
        }

        plugin.getDurabilityService().repairFully(item);
        plugin.getDisplayService().updateDisplay(item);

        plugin.getMessageManager().sendCommand(sender, "repair.success-sender",
                Placeholder.unparsed("player", target.getName()));
        if (target != sender) {
            plugin.getMessageManager().sendCommand(target, "repair.success-target");
        }

        return Command.SINGLE_SUCCESS;
    }

    // ===== Help =====

    private void sendHelp(CommandSender sender) {
        plugin.getMessageManager().sendCommand(sender, "help.header");
        if (sender.hasPermission("oaktools.give")) {
            plugin.getMessageManager().sendCommand(sender, "help.give");
        }
        if (sender.hasPermission("oaktools.reload")) {
            plugin.getMessageManager().sendCommand(sender, "help.reload");
        }
        if (sender.hasPermission("oaktools.info")) {
            plugin.getMessageManager().sendCommand(sender, "help.info");
        }
        if (sender.hasPermission("oaktools.repair")) {
            plugin.getMessageManager().sendCommand(sender, "help.repair");
        }
    }
}
