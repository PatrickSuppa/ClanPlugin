package com.clansplugin.placeholder;

import com.clansplugin.ClansPlugin;
import com.clansplugin.config.MessageManager;
import com.clansplugin.model.Clan;
import com.clansplugin.model.ClanMember;
import com.clansplugin.service.ClanService;
import com.clansplugin.util.RoleUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public class ClansPlaceholderExpansion extends PlaceholderExpansion {
    private final ClansPlugin plugin;
    private final ClanService clanService;
    private final MessageManager messages;

    public ClansPlaceholderExpansion(ClansPlugin plugin, ClanService clanService, MessageManager messages) {
        this.plugin = plugin;
        this.clanService = clanService;
        this.messages = messages;
    }

    @Override
    public String getIdentifier() {
        return "clans";
    }

    @Override
    public String getAuthor() {
        return "Candidato Java";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }

        Clan clan = clanService.getClanByPlayer(player.getUniqueId());
        switch (params.toLowerCase()) {
            case "player_clan":
                return clan == null ? "" : clan.getName();
            case "player_tag":
                return clan == null ? "" : clan.getTag();
            case "player_role":
                if (clan == null) {
                    return "";
                }
                ClanMember member = clan.getMember(player.getUniqueId());
                return member == null ? "" : RoleUtil.display(member.getRole(), messages);
            case "clan_members_online":
                return clan == null ? "0" : String.valueOf(clanService.getOnlineMembersCount(clan));
            default:
                return null;
        }
    }
}
