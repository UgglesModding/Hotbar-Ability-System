package com.abilities.abilitiesplugin;

import com.hypixel.hytale.server.core.Message;

import java.util.ArrayList;
import java.util.List;

public final class AbilityDispatch {

    private static final List<IAbilityPlugin> plugins = new ArrayList<>();

    private AbilityDispatch() {}

    public static void register(IAbilityPlugin plugin) {
        if (plugin == null) return;
        plugins.add(plugin);
    }

    public static boolean dispatch(PackagedAbilityData data, AbilityContext context) {
        for (IAbilityPlugin plugin : plugins) {
            try {
                if (plugin.HCA_DoAbility(data, context)) return true;
            } catch (Throwable t) {
                if (context != null && context.PlayerRef != null) {
                    context.PlayerRef.sendMessage(
                            Message.raw("[CAO] Ability plugin error: " + t.getClass().getSimpleName())
                    );
                }
            }
        }
        return false;
    }
}
