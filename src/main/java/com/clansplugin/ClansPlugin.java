package com.clansplugin;

import com.clansplugin.command.ClanCommand;
import com.clansplugin.config.MessageManager;
import com.clansplugin.db.DatabaseManager;
import com.clansplugin.listener.ChatListener;
import com.clansplugin.listener.PlayerMovementListener;
import com.clansplugin.listener.ProtectionListener;
import com.clansplugin.placeholder.ClansPlaceholderExpansion;
import com.clansplugin.service.ClanService;
import com.clansplugin.service.ConfirmationService;
import com.clansplugin.service.InviteService;
import com.clansplugin.service.TeleportService;
import com.clansplugin.service.WorldGuardHook;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClansPlugin extends JavaPlugin {
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private ClanService clanService;
    private InviteService inviteService;
    private ConfirmationService confirmationService;
    private TeleportService teleportService;
    private WorldGuardHook worldGuardHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.messageManager = new MessageManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.init();

        this.confirmationService = new ConfirmationService(this);
        this.inviteService = new InviteService(this, databaseManager);
        this.teleportService = new TeleportService(this);
        this.worldGuardHook = new WorldGuardHook(this);

        boolean requireWorldGuard = getConfig().getBoolean("territories.require-worldguard", true)
                || getConfig().getString("territories.mode", "WORLDGUARD").equalsIgnoreCase("WORLDGUARD");
        if (requireWorldGuard && !worldGuardHook.isAvailable()) {
            getLogger().severe("WorldGuard non trovato. Installa WorldGuard per usare questo plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.clanService = new ClanService(this, databaseManager, messageManager, inviteService, confirmationService, teleportService, worldGuardHook);
        this.clanService.loadAllCaches();
        this.clanService.syncAllWorldGuardClaims();
        this.inviteService.loadActiveInvites();

        PluginCommand command = getCommand("clan");
        if (command != null) {
            ClanCommand clanCommand = new ClanCommand(this, clanService, messageManager);
            command.setExecutor(clanCommand);
            command.setTabCompleter(clanCommand);
        }

        getServer().getPluginManager().registerEvents(new ProtectionListener(this, clanService, messageManager), this);
        getServer().getPluginManager().registerEvents(new ChatListener(clanService), this);
        getServer().getPluginManager().registerEvents(new PlayerMovementListener(teleportService, messageManager), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ClansPlaceholderExpansion(this, clanService, messageManager).register();
            getLogger().info("Hook PlaceholderAPI attivo.");
        }

        getLogger().info("ClansTerritoryPlugin abilitato correttamente.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public ClanService getClanService() {
        return clanService;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }
}
