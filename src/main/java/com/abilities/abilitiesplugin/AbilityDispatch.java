package com.abilities.abilitiesplugin;

import com.hypixel.hytale.server.core.Message;

import java.util.*;

/**
 * Dispatcher that supports:
 * 1) Local ability handlers (IAbilityPlugin)  [same as before]
 * 2) External command dispatch routes (exact id OR prefix)
 */
public final class AbilityDispatch {

    private static final List<IAbilityPlugin> plugins = new ArrayList<>();

    // exact abilityId -> command template
    private static final Map<String, String> exactCommandRoutes = new HashMap<>();

    // prefix routes checked in insertion order
    private static final List<PrefixRoute> prefixRoutes = new ArrayList<>();

    private AbilityDispatch() {}

    // -------------------------
    // Local handlers (existing)
    // -------------------------

    public static void register(IAbilityPlugin plugin) {
        if (plugin == null) return;
        plugins.add(plugin);
    }

    // -------------------------
    // External dispatch routes
    // -------------------------

    /** Exact match: abilityId -> command template */
    public static void registerCommandRouteExact(String abilityId, String commandTemplate) {
        if (abilityId == null || abilityId.isBlank()) return;
        if (commandTemplate == null || commandTemplate.isBlank()) return;

        exactCommandRoutes.put(ItemIdUtil.normalizeItemId(abilityId), commandTemplate);
    }

    /** Prefix match: anything that starts with prefix routes to command template */
    public static void registerCommandRoutePrefix(String abilityIdPrefix, String commandTemplate) {
        if (abilityIdPrefix == null || abilityIdPrefix.isBlank()) return;
        if (commandTemplate == null || commandTemplate.isBlank()) return;

        prefixRoutes.add(new PrefixRoute(abilityIdPrefix, commandTemplate));
    }

    /**
     * Full dispatch:
     * 1) try local handlers
     * 2) if not handled -> try external command dispatch
     */
    public static boolean dispatch(PackagedAbilityData data, AbilityContext context) {
        // 1) local plugins
        for (IAbilityPlugin plugin : plugins) {
            try {
                if (plugin.HCA_DoAbility(data, context)) return true;
            } catch (Throwable t) {
                if (context != null && context.PlayerRef != null) {
                    context.PlayerRef.sendMessage(
                            Message.raw("[HCA] Ability plugin error: " + t.getClass().getSimpleName())
                    );
                }
            }
        }

        // 2) external command dispatch
        return dispatchExternal(data, context);
    }

    private static boolean dispatchExternal(PackagedAbilityData data, AbilityContext ctx) {
        if (data == null || ctx == null || data.ID == null || data.ID.isBlank()) return false;

        String abilityId = ItemIdUtil.normalizeItemId(data.ID);

        // exact
        String cmd = exactCommandRoutes.get(abilityId);
        if (cmd != null && !cmd.isBlank()) {
            return CommandDispatch.tryRun(cmd, data, ctx);
        }

        // prefix (in order)
        for (PrefixRoute r : prefixRoutes) {
            if (abilityId.startsWith(r.prefix)) {
                return CommandDispatch.tryRun(r.commandTemplate, data, ctx);
            }
        }

        return false;
    }

    private static final class PrefixRoute {
        final String prefix;
        final String commandTemplate;

        PrefixRoute(String prefix, String commandTemplate) {
            this.prefix = ItemIdUtil.normalizeItemId(prefix);
            this.commandTemplate = commandTemplate;
        }
    }
}
