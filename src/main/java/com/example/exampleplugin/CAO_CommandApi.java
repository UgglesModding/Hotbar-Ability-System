package com.example.exampleplugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.lang.reflect.Method;

public final class CAO_CommandApi {

    private static JavaPlugin plugin;

    private CAO_CommandApi() {}

    /** Call this once from ExamplePlugin.setup() */
    public static void Init(JavaPlugin Plugin) {
        plugin = Plugin;
    }

    /** Runs a command "as the player" (best-effort). Command string can be with or without leading '/'. */
    public static boolean TryRunCommandAsPlayer(AbilityContext ctx, String commandLine) {
        if (ctx == null || ctx.playerRef == null) return false;

        String cmd = normalize(commandLine);
        if (cmd.isBlank()) return false;

        // Best effort:
        // 1) Try run via plugin command registry/manager as player
        // 2) If not found, log method hints so we can wire properly
        boolean ok = tryInvokeCommandExecutor(ctx, cmd, true);

        if (!ok) {
            ctx.playerRef.sendMessage(Message.raw("[CAO] Could not run command as player (no executor method found in this build)."));
        }
        return ok;
    }

    /** Runs a command "as server/console" (best-effort). Command string can be with or without leading '/'. */
    public static boolean TryRunCommandAsServer(AbilityContext ctx, String commandLine) {
        if (ctx == null || ctx.playerRef == null) return false;

        String cmd = normalize(commandLine);
        if (cmd.isBlank()) return false;

        boolean ok = tryInvokeCommandExecutor(ctx, cmd, false);

        if (!ok) {
            ctx.playerRef.sendMessage(Message.raw("[CAO] Could not run command as server (no executor method found in this build)."));
        }
        return ok;
    }

    // ----------------------
    // Internals
    // ----------------------

    private static String normalize(String commandLine) {
        if (commandLine == null) return "";
        String cmd = commandLine.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1).trim();
        return cmd;
    }

    /**
     * Tries to locate something in your build that can execute commands.
     * We don't hardcode class names because Hytale server jars vary.
     */
    private static boolean tryInvokeCommandExecutor(AbilityContext ctx, String cmd, boolean asPlayer) {
        if (plugin == null) {
            ctx.playerRef.sendMessage(Message.raw("[CAO] Command API not initialized (call CAO_CommandApi.Init(this) in ExamplePlugin.setup)"));
            return false;
        }

        Object registry = null;
        try {
            // JavaPlugin#getCommandRegistry() exists in your code
            Method m = plugin.getClass().getMethod("getCommandRegistry");
            registry = m.invoke(plugin);
        } catch (Throwable ignored) {}

        // Try common entry points on the registry object
        if (registry != null) {
            if (tryCall(registry, ctx, cmd, asPlayer)) return true;
        }

        // Try on plugin itself as fallback (some builds put it directly on plugin/server)
        if (tryCall(plugin, ctx, cmd, asPlayer)) return true;

        // As a last resort, dump method hints (once) to help wire it correctly
        dumpHints(ctx, registry != null ? registry : plugin);
        return false;
    }

    /**
     * Attempts to invoke methods that look like they execute commands.
     * We try a handful of likely method names + parameter combos.
     */
    private static boolean tryCall(Object target, AbilityContext ctx, String cmd, boolean asPlayer) {
        Method[] methods = target.getClass().getMethods();

        for (Method m : methods) {
            String name = m.getName().toLowerCase();
            if (!(name.contains("execute") || name.contains("dispatch") || name.contains("run"))) continue;
            if (!name.contains("command")) continue;

            Class<?>[] p = m.getParameterTypes();

            try {
                // Pattern A: executeCommand(PlayerRef, String)
                if (p.length == 2 && p[0].isAssignableFrom(ctx.playerRef.getClass()) && p[1] == String.class) {
                    m.invoke(target, ctx.playerRef, cmd);
                    ctx.playerRef.sendMessage(Message.raw("[CAO] Ran command as player: /" + cmd));
                    return true;
                }

                // Pattern B: executeCommand(String, PlayerRef)
                if (p.length == 2 && p[0] == String.class && p[1].isAssignableFrom(ctx.playerRef.getClass())) {
                    m.invoke(target, cmd, ctx.playerRef);
                    ctx.playerRef.sendMessage(Message.raw("[CAO] Ran command as player: /" + cmd));
                    return true;
                }

                // Pattern C: executeCommand(String)  (server/console style)
                if (!asPlayer && p.length == 1 && p[0] == String.class) {
                    m.invoke(target, cmd);
                    ctx.playerRef.sendMessage(Message.raw("[CAO] Ran command as server: /" + cmd));
                    return true;
                }

                // Pattern D: executeCommand(PlayerRef, String, boolean) (player/server toggle)
                if (p.length == 3
                        && p[0].isAssignableFrom(ctx.playerRef.getClass())
                        && p[1] == String.class
                        && (p[2] == boolean.class || p[2] == Boolean.class)) {
                    m.invoke(target, ctx.playerRef, cmd, !asPlayer);
                    ctx.playerRef.sendMessage(Message.raw("[CAO] Ran command (" + (asPlayer ? "player" : "server") + "): /" + cmd));
                    return true;
                }

            } catch (Throwable ignored) {
                // Keep trying other candidates
            }
        }

        return false;
    }

    /** Prints a short hint list of methods that look relevant. */
    private static void dumpHints(AbilityContext ctx, Object target) {
        if (ctx == null || ctx.playerRef == null || target == null) return;

        int shown = 0;
        for (Method m : target.getClass().getMethods()) {
            String n = m.getName().toLowerCase();
            if (!(n.contains("execute") || n.contains("dispatch") || n.contains("run"))) continue;
            if (!n.contains("command")) continue;

            ctx.playerRef.sendMessage(Message.raw("[CAO] Hint: " + m.getName() + "(" + m.getParameterCount() + ") on " + target.getClass().getSimpleName()));
            shown++;
            if (shown >= 12) break;
        }

        if (shown == 0) {
            ctx.playerRef.sendMessage(Message.raw("[CAO] Hint: no *Command* execute/dispatch/run methods found on " + target.getClass().getSimpleName()));
        }
    }
}
