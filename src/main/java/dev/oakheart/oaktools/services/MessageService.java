package dev.oakheart.oaktools.services;

import dev.oakheart.oaktools.OakTools;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles sending messages to players via multiple delivery methods.
 */
public class MessageService {

    private final OakTools plugin;
    private final MiniMessage miniMessage;

    public MessageService(OakTools plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Send a configured message to a player with named placeholder replacement.
     *
     * @param player the player to send the message to
     * @param messageKey the message key in config (e.g., "protection_denied")
     * @param placeholders map of placeholder names to values (e.g., "tool" -> "File")
     */
    public void sendMessage(Player player, String messageKey, Map<String, String> placeholders) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection messageSection = config.getConfigurationSection("messages." + messageKey);

        if (messageSection == null || !messageSection.getBoolean("enabled", true)) {
            return;
        }

        List<String> deliveryMethods = messageSection.getStringList("delivery");
        if (deliveryMethods.isEmpty()) {
            return;
        }

        for (String method : deliveryMethods) {
            switch (method.toLowerCase()) {
                case "actionbar" -> sendActionBar(player, messageSection, placeholders);
                case "chat" -> sendChat(player, messageSection, placeholders);
                case "title" -> sendTitle(player, messageSection, placeholders);
                default -> plugin.getLogger().warning("Unknown message delivery method: " + method);
            }
        }
    }

    /**
     * Send a configured message to a player without placeholders.
     * Convenience method for messages that don't need placeholder replacement.
     *
     * @param player the player to send the message to
     * @param messageKey the message key in config (e.g., "protection_denied")
     */
    public void sendMessage(Player player, String messageKey) {
        sendMessage(player, messageKey, new HashMap<>());
    }

    /**
     * Send an action bar message.
     *
     * @param player the player
     * @param messageSection the message configuration section
     * @param placeholders placeholder map
     */
    private void sendActionBar(Player player, ConfigurationSection messageSection, Map<String, String> placeholders) {
        String content = messageSection.getString("content", "");
        content = replacePlaceholders(content, placeholders);

        Component component = miniMessage.deserialize(content);
        player.sendActionBar(component);
    }

    /**
     * Send a chat message.
     *
     * @param player the player
     * @param messageSection the message configuration section
     * @param placeholders placeholder map
     */
    private void sendChat(Player player, ConfigurationSection messageSection, Map<String, String> placeholders) {
        String content = messageSection.getString("content", "");
        content = replacePlaceholders(content, placeholders);

        Component component = miniMessage.deserialize(content);
        player.sendMessage(component);
    }

    /**
     * Send a title message.
     *
     * @param player the player
     * @param messageSection the message configuration section
     * @param placeholders placeholder map
     */
    private void sendTitle(Player player, ConfigurationSection messageSection, Map<String, String> placeholders) {
        String titleText = messageSection.getString("title", "");
        String subtitleText = messageSection.getString("subtitle", "");

        titleText = replacePlaceholders(titleText, placeholders);
        subtitleText = replacePlaceholders(subtitleText, placeholders);

        Component title = miniMessage.deserialize(titleText);
        Component subtitle = miniMessage.deserialize(subtitleText);

        Title titleObj = Title.title(
                title,
                subtitle,
                Title.Times.times(
                        Duration.ofMillis(500),  // Fade in
                        Duration.ofMillis(2000), // Stay
                        Duration.ofMillis(500)   // Fade out
                )
        );

        player.showTitle(titleObj);
    }

    /**
     * Replace named placeholders in a message.
     * Replaces %placeholder% with values from the map.
     *
     * @param text the text with placeholders
     * @param placeholders map of placeholder names to values (without % symbols)
     * @return the text with placeholders replaced
     */
    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String placeholder = "%" + entry.getKey() + "%";
            String value = entry.getValue();
            if (value != null) {
                text = text.replace(placeholder, value);
            }
        }
        return text;
    }

    /**
     * Send a direct action bar message (not from config).
     *
     * @param player the player
     * @param message the MiniMessage formatted message
     */
    public void sendDirectActionBar(Player player, String message) {
        Component component = miniMessage.deserialize(message);
        player.sendActionBar(component);
    }

    /**
     * Send a direct chat message (not from config).
     *
     * @param player the player
     * @param message the MiniMessage formatted message
     */
    public void sendDirectChat(Player player, String message) {
        Component component = miniMessage.deserialize(message);
        player.sendMessage(component);
    }

    /**
     * Send a command message to a CommandSender (player or console).
     * Command messages are always sent via chat and are located under messages.commands.* in config.
     *
     * @param sender the command sender
     * @param messageKey the message key under commands (e.g., "no_permission", "give.success_sender")
     * @param placeholders map of placeholder names to values (e.g., "player" -> "Steve")
     */
    public void sendCommandMessage(CommandSender sender, String messageKey, Map<String, String> placeholders) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String path = "messages.commands." + messageKey;
        String content = config.getString(path, null);

        if (content == null) {
            plugin.getLogger().warning("Missing command message config: " + path);
            return;
        }

        content = replacePlaceholders(content, placeholders);
        Component component = miniMessage.deserialize(content);
        sender.sendMessage(component);
    }

    /**
     * Send a command message to a CommandSender without placeholders.
     * Convenience method for command messages that don't need placeholder replacement.
     *
     * @param sender the command sender
     * @param messageKey the message key under commands (e.g., "no_permission")
     */
    public void sendCommandMessage(CommandSender sender, String messageKey) {
        sendCommandMessage(sender, messageKey, new HashMap<>());
    }
}
