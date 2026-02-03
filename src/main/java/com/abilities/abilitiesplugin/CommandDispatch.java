package com.abilities.abilitiesplugin;

import com.hypixel.hytale.server.core.Message;

import java.lang.reflect.Method;

/**
 * Executes a command by reflection to avoid hard dependencies.
 *
 * Tokens:
 * {player} {abilityId} {slot} {value} {power} {remaining} {max}
 *
 * Example:
 * "katanas_doability {abilityId} {slot} {value} {power}"
 */
public final class CommandDispatch {

    private CommandDispatch() {}

    public static boolean tryRun(String commandTemplate, PackagedAbilityData data, AbilityContext ctx) {
        if (commandTemplate == null || commandTemplate.isBlank()) return false;

        String cmd = expand(commandTemplate, data, ctx);

        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        boolean ok = tryRunOnPlayerRef(ctx, cmd);
        if (!ok && ctx != null && ctx.PlayerRef != null) {
            ctx.PlayerRef.sendMessage(Message.raw("[HCA] Could not execute command dispatch: " + cmd));
        }
        return ok;
    }

    private static String expand(String tpl, PackagedAbilityData data, AbilityContext ctx) {
        String player = (ctx == null || ctx.PlayerRef == null) ? "" : ctx.PlayerRef.getUsername();

        return tpl
                .replace("{player}", player)
                .replace("{abilityId}", safe(data.ID))
                .replace("{slot}", Integer.toString(data.Slot1to9))
                .replace("{value}", Integer.toString(data.AbilityValue))
                .replace("{power}", Float.toString(data.PowerMultiplier))
                .replace("{remaining}", Integer.toString(data.RemainingUses))
                .replace("{max}", Integer.toString(data.MaxUses));
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    private static boolean tryRunOnPlayerRef(AbilityContext ctx, String cmdNoSlash) {
        if (ctx == null || ctx.PlayerRef == null) return false;

        Object playerRef = ctx.PlayerRef;
        Class<?> c = playerRef.getClass();

        String[] methods = new String[] {
                "executeCommand",
                "runCommand",
                "dispatchCommand",
                "performCommand"
        };

        for (String m : methods) {
            try {
                Method mm = c.getMethod(m, String.class);
                mm.invoke(playerRef, cmdNoSlash);
                return true;
            } catch (Throwable ignored) {}
        }

        try {
            Method chat = c.getMethod("chat", String.class);
            chat.invoke(playerRef, "/" + cmdNoSlash);
            return true;
        } catch (Throwable ignored) {}

        try {
            Method sendChat = c.getMethod("sendChatMessage", String.class);
            sendChat.invoke(playerRef, "/" + cmdNoSlash);
            return true;
        } catch (Throwable ignored) {}

        return false;
    }
}
