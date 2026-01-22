package com.example.exampleplugin;

import com.hypixel.hytale.server.core.Message;

public class CAO_DoAbility implements IAbilityPlugin {

    @Override
    public boolean CAO_DoAbility(String id, AbilityContext ctx) {
        if (ctx == null || ctx.playerRef == null) return false;

        switch (id) {
            case "combat_overhaul:dagger_leap":
                ctx.playerRef.sendMessage(Message.raw("[CAO] dagger_leap executed (stub)"));
                return true;

            default:
                return false;
        }
    }
}
