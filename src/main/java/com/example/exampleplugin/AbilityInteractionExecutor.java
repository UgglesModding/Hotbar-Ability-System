package com.example.exampleplugin;

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

            // Try World first (many engines execute interactions through world/services)
            if (world != null) {
                if (invokeIfExists(world, "executeRootInteraction", playerRef, rootName)) return true;
                if (invokeIfExists(world, "runRootInteraction", playerRef, rootName)) return true;
                if (invokeIfExists(world, "executeInteraction", playerRef, rootName)) return true;
                if (invokeIfExists(world, "runInteraction", playerRef, rootName)) return true;
                if (invokeIfExists(world, "executeInteractionChain", playerRef, rootName)) return true;
                if (invokeIfExists(world, "startInteraction", playerRef, rootName)) return true;
            }

            // Try PlayerRef (sometimes it owns interaction triggers)
            if (playerRef != null) {
                if (invokeIfExists(playerRef, "executeRootInteraction", rootName)) return true;
                if (invokeIfExists(playerRef, "runRootInteraction", rootName)) return true;
                if (invokeIfExists(playerRef, "executeInteraction", rootName)) return true;
                if (invokeIfExists(playerRef, "runInteraction", rootName)) return true;
                if (invokeIfExists(playerRef, "startInteraction", rootName)) return true;
            }

            // Try Player (again, just in case)
            if (player != null) {
                if (invokeIfExists(player, "executeRootInteraction", rootName)) return true;
                if (invokeIfExists(player, "runRootInteraction", rootName)) return true;
                if (invokeIfExists(player, "executeInteraction", rootName)) return true;
                if (invokeIfExists(player, "runInteraction", rootName)) return true;
                if (invokeIfExists(player, "startInteraction", rootName)) return true;
            }

            return false;
        }

        private static boolean invokeIfExists(Object target, String methodName, Object... args) {
            try {
                // Try exact signature match by runtime arg classes
                Class<?>[] sig = new Class<?>[args.length];
                for (int i = 0; i < args.length; i++) sig[i] = args[i].getClass();

                Method m = target.getClass().getMethod(methodName, sig);
                m.setAccessible(true);
                m.invoke(target, args);
                return true;
            } catch (Throwable ignored) {
                // Try looser matching: find any method with same name + arg count
                try {
                    for (Method m : target.getClass().getMethods()) {
                        if (!m.getName().equals(methodName)) continue;
                        if (m.getParameterCount() != args.length) continue;

                        m.setAccessible(true);
                        m.invoke(target, args);
                        return true;
                    }
                } catch (Throwable ignored2) {}
                return false;
            }
        }
    }
}
