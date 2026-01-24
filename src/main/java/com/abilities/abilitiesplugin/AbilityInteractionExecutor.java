package com.abilities.abilitiesplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class AbilityInteractionExecutor {

    private final Map<String, BiFunction<PlayerRef, Ctx, Boolean>> handlers = new HashMap<>();

    public AbilityInteractionExecutor() {
        handlers.put("Ability_Test", (playerRef, ctx) -> {
            boolean ran = RootInteractionRunner.tryExecute(ctx.world, ctx.playerRef, ctx.player, "Ability_Test");
            if (!ran) {
                playerRef.sendMessage(Message.raw(
                        "[Ability_Test] Tried to execute RootInteraction but no engine method matched (World/PlayerRef/Player)."
                ));
            }
            return true;
        });
    }

    public boolean execute(String interactionName, PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref, World world) {
        if (interactionName == null || interactionName.isBlank()) return false;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return false;

        var fn = handlers.get(interactionName);
        if (fn == null) return false;

        return fn.apply(playerRef, new Ctx(world, playerRef, player));
    }

    public static final class Ctx {
        public final World world;
        public final PlayerRef playerRef;
        public final Player player;

        public Ctx(World world, PlayerRef playerRef, Player player) {
            this.world = world;
            this.playerRef = playerRef;
            this.player = player;
        }
    }

    public static final class RootInteractionRunner {

        public static boolean tryExecute(World world, PlayerRef playerRef, Player player, String rootName) {
            if (rootName == null || rootName.isBlank()) return false;

            //logic not working
            return false;
        }
    }
}
