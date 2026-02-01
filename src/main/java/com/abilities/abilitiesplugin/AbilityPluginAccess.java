package com.abilities.abilitiesplugin;

import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class AbilityPluginAccess {

    // Set once during plugin setup
    static AbilityHotbarState StateRef;

    private AbilityPluginAccess() {}


    public static void Init(AbilityHotbarState state) {
        StateRef = state;
    }


    public static AbilityHotbarState.State State(PlayerRef playerRef) {
        if (StateRef == null) {
            throw new IllegalStateException("AbilityPlugin not initialized");
        }
        return StateRef.get(playerRef.getUsername());
    }
}
