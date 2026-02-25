package dev.oakheart.oaktools.message;

import dev.oakheart.oaktools.OakTools;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;

public class MessageManager {

    private final OakTools plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageManager(OakTools plugin) {
        this.plugin = plugin;
    }

    /**
     * Send a configured message to a player with named placeholder replacement.
     * Uses the text/display format from config.
     */
    public void sendMessage(Player player, String messageKey, Map<String, String> placeholders) {
        String text = plugin.getConfigManager().getMessage(messageKey);
        if (text == null || text.isEmpty()) {
            return;
        }

        String display = plugin.getConfigManager().getMessageDisplay(messageKey);
        TagResolver[] resolvers = buildResolvers(placeholders);
        Component component = miniMessage.deserialize(text, resolvers);

        switch (display) {
            case "action_bar" -> player.sendActionBar(component);
            case "title" -> player.showTitle(net.kyori.adventure.title.Title.title(
                    component, Component.empty()));
            default -> player.sendMessage(component);
        }
    }

    /**
     * Send a configured message to a player without placeholders.
     */
    public void sendMessage(Player player, String messageKey) {
        sendMessage(player, messageKey, Map.of());
    }

    /**
     * Send a command message to a CommandSender.
     * Command messages are simple strings under messages.commands.* in config.
     */
    public void sendCommandMessage(CommandSender sender, String messageKey, Map<String, String> placeholders) {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Build dot-separated path: messages.commands.<messageKey with . separators>
        String path = "messages.commands." + messageKey;
        String content = config.getString(path, null);

        if (content == null) {
            plugin.getLogger().warning("Missing command message config: " + path);
            return;
        }
        if (content.isEmpty()) {
            return;
        }

        TagResolver[] resolvers = buildResolvers(placeholders);
        Component component = miniMessage.deserialize(content, resolvers);
        sender.sendMessage(component);
    }

    /**
     * Send a command message without placeholders.
     */
    public void sendCommandMessage(CommandSender sender, String messageKey) {
        sendCommandMessage(sender, messageKey, Map.of());
    }

    /**
     * Build an array of TagResolvers from a placeholder map.
     */
    private TagResolver[] buildResolvers(Map<String, String> placeholders) {
        return placeholders.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new);
    }

}
