package com.example.exampleplugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class AbilityInteractionExecutor {

    private final Map<String, Consumer<PlayerRef>> handlers = new HashMap<>();

    public AbilityInteractionExecutor() {
        // Register interaction handlers here
        handlers.put("Ability_Test", playerRef ->
                playerRef.sendMessage(Message.raw("[Ability_Test] fired!"))
        );
    }

    public boolean execute(String interactionName, PlayerRef playerRef) {
        if (interactionName == null || interactionName.isBlank()) return false;

        Consumer<PlayerRef> fn = handlers.get(interactionName);
        if (fn == null) return false;

        fn.accept(playerRef);
        return true;
    }
}
