package com.clansplugin.util;

import com.clansplugin.config.MessageManager;
import com.clansplugin.model.ClanRole;

public final class RoleUtil {
    private RoleUtil() {
    }

    public static String display(ClanRole role, MessageManager messages) {
        return switch (role) {
            case LEADER -> messages.raw("role-leader");
            case OFFICER -> messages.raw("role-officer");
            case MEMBER -> messages.raw("role-member");
        };
    }
}
