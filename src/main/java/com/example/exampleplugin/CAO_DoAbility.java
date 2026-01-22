package com.example.exampleplugin;

import com.example.exampleplugin.*;
import com.hypixel.hytale.server.core.Message;

public class CAO_DoAbility implements IAbilityPlugin {

    @Override
    public boolean CAO_DoAbility(PackagedAbilityData Data, AbilityContext Context) {
        if (Data == null || Context == null || Context.playerRef == null) return false;

        switch (Data.ID) {

            case "combat_overhaul:dagger_leap":
                Context.playerRef.sendMessage(Message.raw(
                        "[CAO] dagger_leap | Power=" + Data.PowerMultiplier +
                                " | Remaining=" + Data.RemainingUses
                ));
                return true;

            default:
                return false;
        }
    }
}
