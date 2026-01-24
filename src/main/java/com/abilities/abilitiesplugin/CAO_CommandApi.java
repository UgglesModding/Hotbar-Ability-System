package com.abilities.abilitiesplugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Locale;

public final class CAO_CommandApi {

    private static JavaPlugin plugin;

    private CAO_CommandApi() {}

    /** Call this once from ExamplePlugin.setup() */
    public static void Init(JavaPlugin Plugin) {
        plugin = Plugin;
    }

    /** Runs a command as the player (best-effort). Accepts "/heal" or "heal". */
    public static boolean TryRunCommandAsPlayer(AbilityContext ctx, String commandLine) {
        if (ctx == null || ctx.playerRef == null) return false;

        String cmd = normalize(commandLine);
        if (cmd.isBlank()) return false;

        // 1) BEST bet: route through player chat -> command system
        if (tryChatAsPlayer(ctx, cmd)) return true;

        // 2) Try registry/dispatcher reflective execution
        boolean ok = tryInvokeCommandExecutor(ctx, cmd, true);

        if (!ok) {
            ctx.playerRef.sendMessage(Message.raw("[CAO] Could not run command as player."));
        }
        return ok;
    }

    /** Runs a command as server/console (best-effort). Accepts "/heal" or "heal". */
    public static boolean TryRunCommandAsServer(AbilityContext ctx, String commandLine) {
        if (ctx == null || ctx.playerRef == null) return false;

        String cmd = normalize(commandLine);
        if (cmd.isBlank()) return false;

        boolean ok = tryInvokeCommandExecutor(ctx, cmd, false);

        if (!ok) {
            ctx.playerRef.sendMessage(Message.raw("[CAO] Could not run command as server/console."));
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
     * Many builds run commands by sending a chat message beginning with '/'.
     * This is the most likely way to get "as player" semantics without knowing dispatcher APIs.
     */
    private static boolean tryChatAsPlayer(AbilityContext ctx, String cmdNoSlash) {
        String chatLine = "/" + cmdNoSlash;

        // Try common method names on PlayerRef
        if (invokeFirstMatching(ctx.playerRef, new String[]{
                "chat", "sendChat", "sendChatMessage", "sendMessageAsChat", "say", "sendText"
        }, new Class<?>[]{String.class}, new Object[]{chatLine})) {
            return true;
        }

        // Try common method names on ctx.player (if your AbilityContext has it)
        if (ctx.player != null) {
            if (invokeFirstMatching(ctx.player, new String[]{
                    "chat", "sendChat", "sendChatMessage", "say"
            }, new Class<?>[]{String.class}, new Object[]{chatLine})) {
                return true;
            }
        }

        return false;
    }

    /**
     * Tries to locate something in your build that can execute commands.
     * We avoid hardcoding class names; we probe registry and plugin and a few likely accessors.
     */
    private static boolean tryInvokeCommandExecutor(AbilityContext ctx, String cmdNoSlash, boolean asPlayer) {
        if (plugin == null) {
            ctx.playerRef.sendMessage(Message.raw("[CAO] Command API not initialized (call CAO_CommandApi.Init(this) in setup)."));
            return false;
        }

        // Try to retrieve "command registry/dispatcher/manager" objects from plugin
        Object registry = tryGet(plugin, "getCommandRegistry");
        Object dispatcher = tryGet(plugin, "getCommandDispatcher");
        Object commandManager = tryGet(plugin, "getCommandManager");
        Object server = tryGet(plugin, "getServer");

        // Also try from server if available
        if (server != null) {
            if (dispatcher == null) dispatcher = tryGet(server, "getCommandDispatcher");
            if (commandManager == null) commandManager = tryGet(server, "getCommandManager");
            if (registry == null) registry = tryGet(server, "getCommandRegistry");
        }

        // 1) Try likely objects in order
        if (tryExecuteOnTarget(registry, ctx, cmdNoSlash, asPlayer)) return true;
        if (tryExecuteOnTarget(dispatcher, ctx, cmdNoSlash, asPlayer)) return true;
        if (tryExecuteOnTarget(commandManager, ctx, cmdNoSlash, asPlayer)) return true;
        if (tryExecuteOnTarget(server, ctx, cmdNoSlash, asPlayer)) return true;

        // 2) Try on plugin itself (some builds keep the executor on plugin)
        if (tryExecuteOnTarget(plugin, ctx, cmdNoSlash, asPlayer)) return true;

        // 3) If nothing matched, dump useful candidates (not just names containing "command")
        dumpExecutionHints(ctx, registry != null ? registry : (dispatcher != null ? dispatcher : (commandManager != null ? commandManager : plugin)));
        return false;
    }

    private static Object tryGet(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Execute cmd using a variety of likely method names + parameter combos.
     * This is the part that makes it actually work across different server builds.
     */
    private static boolean tryExecuteOnTarget(Object target, AbilityContext ctx, String cmdNoSlash, boolean asPlayer) {
        if (target == null) return false;

        // We'll try both with and without leading slash depending on executor expectations
        String cmd = cmdNoSlash;
        String cmdWithSlash = "/" + cmdNoSlash;

        Method[] methods = target.getClass().getMethods();

        for (Method m : methods) {
            String name = m.getName().toLowerCase(Locale.ROOT);

            // Accept a wider set of likely executor names
            boolean looksExecutable =
                    name.contains("execute") ||
                            name.contains("dispatch") ||
                            name.contains("run") ||
                            name.contains("perform") ||
                            name.contains("handle");

            // Also accept explicit "command" methods even if not in the above list
            if (!looksExecutable && !name.contains("command")) continue;

            Class<?>[] p = m.getParameterTypes();

            try {
                // Pattern 1: (PlayerRef, String)
                if (p.length == 2 && p[1] == String.class && p[0].isAssignableFrom(ctx.playerRef.getClass())) {
                    m.invoke(target, ctx.playerRef, asPlayer ? cmdWithSlash : cmd);
                    return true;
                }

                // Pattern 2: (String, PlayerRef)
                if (p.length == 2 && p[0] == String.class && p[1].isAssignableFrom(ctx.playerRef.getClass())) {
                    m.invoke(target, asPlayer ? cmdWithSlash : cmd, ctx.playerRef);
                    return true;
                }

                // Pattern 3: (String) — usually console/server
                if (!asPlayer && p.length == 1 && p[0] == String.class) {
                    m.invoke(target, cmd);
                    return true;
                }

                // Pattern 4: (String, boolean) or (String, Boolean) where boolean might mean "asConsole"
                if (p.length == 2 && p[0] == String.class && (p[1] == boolean.class || p[1] == Boolean.class)) {
                    // guessing: true might mean "asConsole"
                    boolean asConsole = !asPlayer;
                    m.invoke(target, cmd, asConsole);
                    return true;
                }

                // Pattern 5: (PlayerRef, String, boolean) or (PlayerRef, String, Boolean)
                if (p.length == 3
                        && p[1] == String.class
                        && p[0].isAssignableFrom(ctx.playerRef.getClass())
                        && (p[2] == boolean.class || p[2] == Boolean.class)) {
                    boolean asConsole = !asPlayer;
                    m.invoke(target, ctx.playerRef, asPlayer ? cmdWithSlash : cmd, asConsole);
                    return true;
                }

            } catch (Throwable ignored) {
                // keep scanning candidates
            }
        }

        return false;
    }

    private static boolean invokeFirstMatching(Object target, String[] methodNames, Class<?>[] paramTypes, Object[] args) {
        if (target == null) return false;
        for (String n : methodNames) {
            try {
                Method m = target.getClass().getMethod(n, paramTypes);
                m.invoke(target, args);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /**
     * Dumps the most promising methods so you can paste them back to me,
     * and then we can hard-wire the exact method signature (no guessing).
     */
    private static void dumpExecutionHints(AbilityContext ctx, Object target) {
        if (ctx == null || ctx.playerRef == null || target == null) return;

        ctx.playerRef.sendMessage(Message.raw("[CAO] Command exec not found. Dumping candidates on: " + target.getClass().getName()));

        int shown = 0;
        for (Method m : target.getClass().getMethods()) {
            String name = m.getName().toLowerCase(Locale.ROOT);

            boolean looksExecutable =
                    name.contains("execute") ||
                            name.contains("dispatch") ||
                            name.contains("run") ||
                            name.contains("perform") ||
                            name.contains("handle") ||
                            name.contains("command");

            if (!looksExecutable) continue;

            Class<?>[] p = m.getParameterTypes();
            StringBuilder sig = new StringBuilder();
            sig.append(m.getName()).append("(");
            for (int i = 0; i < p.length; i++) {
                if (i > 0) sig.append(", ");
                sig.append(p[i].getSimpleName());
            }
            sig.append(")");

            ctx.playerRef.sendMessage(Message.raw("[CAO] Hint: " + sig));
            shown++;
            if (shown >= 18) break;
        }

        if (shown == 0) {
            ctx.playerRef.sendMessage(Message.raw("[CAO] Hint: no execute/dispatch/run/perform/handle/command methods found."));
        }
    }
    private static void dumpCommandManagerMethods(Object commandManager) {
        System.out.println("[CAO] CommandManager class = " + commandManager.getClass().getName());
        for (var m : commandManager.getClass().getMethods()) {
            String n = m.getName().toLowerCase();
            if (!n.contains("execute") && !n.contains("dispatch") && !n.contains("run")) continue;

            StringBuilder sig = new StringBuilder();
            sig.append(m.getName()).append("(");
            Class<?>[] p = m.getParameterTypes();
            for (int i = 0; i < p.length; i++) {
                if (i > 0) sig.append(", ");
                sig.append(p[i].getName());
            }
            sig.append(")");
            System.out.println("[CAO] " + sig);
        }
    }

}
