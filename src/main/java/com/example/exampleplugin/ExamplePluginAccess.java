package com.example.exampleplugin;

import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class ExamplePluginAccess {

    // Set once during plugin setup
    static AbilityHotbarState StateRef;

    private ExamplePluginAccess() {}

    /** Called once from ExamplePlugin.setup() */
    public static void Init(AbilityHotbarState state) {
        StateRef = state;
    }

    /** Access a player's hotbar state safely */
    public static AbilityHotbarState.State State(PlayerRef playerRef) {
        if (StateRef == null) {
            throw new IllegalStateException("ExamplePluginAccess not initialized");
        }
        return StateRef.get(playerRef.getUsername());
    }
}
