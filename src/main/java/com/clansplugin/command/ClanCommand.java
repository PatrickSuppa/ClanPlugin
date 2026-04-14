package com.clansplugin.command;

import com.clansplugin.ClansPlugin;
import com.clansplugin.config.MessageManager;
import com.clansplugin.model.Clan;
import com.clansplugin.model.ClanRole;
import com.clansplugin.service.ClanService;
import com.clansplugin.util.RoleUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ClanCommand implements CommandExecutor, TabCompleter {
    private final ClansPlugin plugin;
    private final ClanService service;
    private final MessageManager messages;

    public ClanCommand(ClansPlugin plugin, ClanService service, MessageManager messages) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "only-player");
            return true;
        }

        if (args.length == 0) {
            messages.getList("help-lines").forEach(player::sendMessage);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "disband" -> handleDisband(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player);
            case "deny" -> handleDeny(player);
            case "kick" -> handleKick(player, args);
            case "promote" -> handlePromote(player, args);
            case "demote" -> handleDemote(player, args);
            case "chat" -> handleChat(player, args);
            case "claim" -> handleClaim(player);
            case "unclaim" -> handleUnclaim(player, args);
            case "home" -> handleHome(player);
            case "sethome" -> handleSetHome(player);
            case "info" -> handleInfo(player, args);
            default -> messages.send(player, "unknown-subcommand");
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            messages.send(player, "usage-create");
            return;
        }
        if (service.getClanByPlayer(player.getUniqueId()) != null) {
            messages.send(player, "already-in-clan");
            return;
        }
        if (!service.isValidClanName(args[1])) {
            messages.send(player, "invalid-name");
            return;
        }
        if (!service.isValidClanTag(args[2])) {
            messages.send(player, "invalid-tag");
            return;
        }
        if (service.getClanByName(args[1]) != null || service.getClanByTag(args[2]) != null) {
            messages.send(player, "name-or-tag-used");
            return;
        }

        if (service.createClan(player, args[1], args[2])) {
            messages.sendRaw(player, messages.raw("clan-created").replace("%clan%", args[1]).replace("%tag%", args[2]));
        } else {
            messages.send(player, "name-or-tag-used");
        }
    }

    private void handleDisband(Player player, String[] args) {
        if (service.getClanByPlayer(player.getUniqueId()) == null) {
            messages.send(player, "no-clan");
            return;
        }
        if (!service.isLeader(player.getUniqueId())) {
            messages.send(player, "no-permission");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            messages.sendRaw(player, messages.raw("clan-disband-confirm")
                    .replace("%seconds%", String.valueOf(service.getConfirmationService().expireSeconds())));
            return;
        }
        if (service.getConfirmationService().confirmRequired(player.getUniqueId(), "disband")) {
            messages.sendRaw(player, messages.raw("clan-disband-confirm")
                    .replace("%seconds%", String.valueOf(service.getConfirmationService().expireSeconds())));
            return;
        }

        String clanName = service.getClanByPlayer(player.getUniqueId()).getName();
        if (service.disbandClan(player)) {
            messages.sendRaw(player, messages.raw("clan-disbanded").replace("%clan%", clanName));
        }
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "usage-invite");
            return;
        }

        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "player-not-found");
            return;
        }
        if (service.getClanByPlayer(target.getUniqueId()) != null) {
            messages.send(player, "already-has-clan");
            return;
        }

        if (service.sendInvite(player, target)) {
            Clan clan = service.getClanByPlayer(player.getUniqueId());
            messages.sendRaw(player, messages.raw("invite-sent").replace("%player%", target.getName()));
            target.sendMessage(messages.raw("invite-received")
                    .replace("%clan%", clan.getName())
                    .replace("%sender%", player.getName()));
        } else {
            messages.send(player, "no-permission");
        }
    }

    private void handleAccept(Player player) {
        var invite = service.getInviteService().get(player.getUniqueId());
        if (invite == null) {
            messages.send(player, "invite-none");
            return;
        }

        Clan invitedClan = service.getClanById(invite.clanId());
        String clanName = invitedClan == null ? "" : invitedClan.getName();
        if (service.acceptInvite(player)) {
            messages.sendRaw(player, messages.raw("invite-accepted").replace("%clan%", clanName));
        }
    }

    private void handleDeny(Player player) {
        Clan clan = service.denyInvite(player);
        if (clan == null) {
            messages.send(player, "invite-none");
            return;
        }
        messages.sendRaw(player, messages.raw("invite-denied").replace("%clan%", clan.getName()));
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "usage-kick");
            return;
        }

        var target = plugin.getServer().getOfflinePlayer(args[1]);
        if (player.getUniqueId().equals(target.getUniqueId())) {
            messages.send(player, "cannot-kick-self");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            messages.sendRaw(player, messages.raw("kick-confirm")
                    .replace("%player%", args[1])
                    .replace("%seconds%", String.valueOf(service.getConfirmationService().expireSeconds())));
            return;
        }
        if (service.getConfirmationService().confirmRequired(player.getUniqueId(), "kick:" + target.getUniqueId())) {
            messages.sendRaw(player, messages.raw("kick-confirm")
                    .replace("%player%", args[1])
                    .replace("%seconds%", String.valueOf(service.getConfirmationService().expireSeconds())));
            return;
        }

        if (service.kickMember(player, target)) {
            String targetName = target.getName() == null ? args[1] : target.getName();
            messages.sendRaw(player, messages.raw("member-kicked").replace("%player%", targetName));
            Player online = plugin.getServer().getPlayer(target.getUniqueId());
            Clan actorClan = service.getClanByPlayer(player.getUniqueId());
            if (online != null && actorClan != null) {
                online.sendMessage(messages.raw("you-were-kicked").replace("%clan%", actorClan.getName()));
            }
        } else {
            messages.send(player, "no-permission");
        }
    }

    private void handlePromote(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "usage-promote");
            return;
        }

        var target = plugin.getServer().getOfflinePlayer(args[1]);
        if (service.promote(player, target)) {
            Clan clan = service.getClanByPlayer(player.getUniqueId());
            ClanRole newRole = clan.getMember(target.getUniqueId()).getRole();
            messages.sendRaw(player, messages.raw("promoted")
                    .replace("%player%", target.getName() == null ? args[1] : target.getName())
                    .replace("%role%", RoleUtil.display(newRole, messages)));
        } else {
            messages.send(player, "cannot-promote");
        }
    }

    private void handleDemote(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "usage-demote");
            return;
        }

        var target = plugin.getServer().getOfflinePlayer(args[1]);
        if (service.demote(player, target)) {
            Clan clan = service.getClanByPlayer(player.getUniqueId());
            ClanRole newRole = clan.getMember(target.getUniqueId()).getRole();
            messages.sendRaw(player, messages.raw("demoted")
                    .replace("%player%", target.getName() == null ? args[1] : target.getName())
                    .replace("%role%", RoleUtil.display(newRole, messages)));
        } else {
            messages.send(player, "cannot-demote");
        }
    }

    private void handleChat(Player player, String[] args) {
        if (service.getClanByPlayer(player.getUniqueId()) == null) {
            messages.send(player, "no-clan");
            return;
        }
        if (args.length == 1) {
            service.toggleClanChat(player.getUniqueId());
            messages.send(player, service.isClanChatEnabled(player.getUniqueId()) ? "clan-chat-toggle-on" : "clan-chat-toggle-off");
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        service.sendClanMessage(player, text);
    }

    private void handleClaim(Player player) {
        Clan owner = service.getClanAt(player.getChunk());
        if (owner != null) {
            messages.sendRaw(player, messages.raw("claim-already-owned").replace("%clan%", owner.getName()));
            return;
        }

        if (service.claim(player)) {
            messages.send(player, "claim-success");
        } else {
            messages.send(player, "claim-limit");
        }
    }

    private void handleUnclaim(Player player, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            messages.sendRaw(player, messages.raw("unclaim-confirm")
                    .replace("%seconds%", String.valueOf(service.getConfirmationService().expireSeconds())));
            return;
        }
        if (service.getConfirmationService().confirmRequired(player.getUniqueId(), "unclaim")) {
            messages.sendRaw(player, messages.raw("unclaim-confirm")
                    .replace("%seconds%", String.valueOf(service.getConfirmationService().expireSeconds())));
            return;
        }

        if (service.unclaim(player)) {
            messages.send(player, "unclaim-success");
        } else {
            messages.send(player, "not-in-own-claim");
        }
    }

    private void handleHome(Player player) {
        Clan clan = service.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            messages.send(player, "no-clan");
            return;
        }
        if (clan.getHome() == null) {
            messages.send(player, "home-not-set");
            return;
        }

        messages.sendRaw(player, messages.raw("teleporting-home")
                .replace("%seconds%", String.valueOf(plugin.getConfig().getInt("homes.teleport-delay-seconds", 3))));
        service.teleportHome(player);
    }

    private void handleSetHome(Player player) {
        if (service.setHome(player)) {
            messages.send(player, "home-set");
        } else {
            messages.send(player, "no-permission");
        }
    }

    private void handleInfo(Player player, String[] args) {
        Clan clan = args.length >= 2 ? service.getClanByName(args[1]) : service.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            messages.send(player, "clan-not-found");
            return;
        }

        String leaderName = plugin.getServer().getOfflinePlayer(clan.getLeaderUuid()).getName();
        messages.sendRaw(player, messages.raw("clan-info-header")
                .replace("%clan%", clan.getName())
                .replace("%tag%", clan.getTag()));
        messages.sendRaw(player, messages.raw("clan-info-leader")
                .replace("%leader%", leaderName == null ? clan.getLeaderUuid().toString() : leaderName));
        messages.sendRaw(player, messages.raw("clan-info-members")
                .replace("%members%", String.valueOf(clan.getMembers().size())));
        messages.sendRaw(player, messages.raw("clan-info-online")
                .replace("%online%", String.valueOf(service.getOnlineMembersCount(clan))));
        messages.sendRaw(player, messages.raw("clan-info-claims")
                .replace("%claims%", String.valueOf(service.getClaimCount(clan.getId()))));
        messages.sendRaw(player, messages.raw("clan-info-created")
                .replace("%created%", clan.getCreatedAt().toString()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], List.of("create", "disband", "invite", "accept", "deny", "kick", "promote", "demote", "chat", "claim", "unclaim", "home", "sethome", "info"));
        }
        if (args.length == 2 && List.of("invite", "kick", "promote", "demote").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>();
            plugin.getServer().getOnlinePlayers().forEach(online -> names.add(online.getName()));
            return partial(args[1], names);
        }
        if (args.length == 2 && List.of("disband", "unclaim").contains(args[0].toLowerCase(Locale.ROOT))) {
            return partial(args[1], List.of("confirm"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("kick")) {
            return partial(args[2], List.of("confirm"));
        }
        return List.of();
    }

    private List<String> partial(String token, List<String> values) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> output = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                output.add(value);
            }
        }
        return output;
    }
}
