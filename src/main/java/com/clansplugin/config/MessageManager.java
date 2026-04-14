package com.clansplugin.config;

import com.clansplugin.ClansPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MessageManager {
    private final FileConfiguration messages;

    public MessageManager(ClansPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path) {
        String prefix = color(messages.getString("prefix", "&8[&aClan&8] "));
        String value = color(messages.getString(path, path));
        return value.startsWith(prefix) ? value : prefix + value;
    }

    public String raw(String path) {
        return color(messages.getString(path, path));
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    public List<String> getList(String path) {
        List<String> output = new ArrayList<>();
        for (String line : messages.getStringList(path)) {
            output.add(color(line));
        }
        return output;
    }

    public String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
