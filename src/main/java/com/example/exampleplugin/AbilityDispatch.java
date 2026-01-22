package com.example.exampleplugin;

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

    public static boolean dispatch(String id, AbilityContext ctx) {
        for (IAbilityPlugin p : plugins) {
            try {
                if (p.CAO_DoAbility(id, ctx)) return true;
            } catch (Throwable t) {
                if (ctx != null && ctx.playerRef != null) {
                    ctx.playerRef.sendMessage(Message.raw(
                            "[CAO] Ability plugin error: " + t.getClass().getSimpleName()
                    ));
                }
            }
        }
        return false;
    }
}
