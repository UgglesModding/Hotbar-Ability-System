package com.abilities.abilitiesplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.xml.stream.events.EntityReference;
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
                        "[Ability_Test] Tried to execute RootInteraction but no engine method matched. " +
                                "This means we still need the correct engine call for starting an Interaction by id."
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

            // 1) Try direct calls on World / PlayerRef / Player using common method names.
            //    (We attempt multiple signatures: (String), (String, PlayerRef), (String, Player), (String, PlayerRef, Player), etc.)
            if (tryInvokeCommon(world, rootName, world, playerRef, player)) return true;
            if (tryInvokeCommon(playerRef, rootName, world, playerRef, player)) return true;
            if (tryInvokeCommon(player, rootName, world, playerRef, player)) return true;

            // 2) Try through PluginManager (sometimes registries live here).
            try {
                Object pm = PluginManager.get();
                if (pm != null) {
                    if (tryInvokeCommon(pm, rootName, world, playerRef, player)) return true;

                    // Try “getInteractionRegistry” or similar, then invoke on that.
                    Object registry =
                            invokeNoArgs(pm, "getInteractionRegistry");
                    if (registry == null) registry = invokeNoArgs(pm, "getInteractions");
                    if (registry == null) registry = invokeNoArgs(pm, "getInteractionManager");
                    if (registry != null) {
                        if (tryInvokeCommon(registry, rootName, world, playerRef, player)) return true;
                    }
                }
            } catch (Throwable ignored) {}

            // 3) Nothing matched.
            return false;
        }

        // ----------------------------
        // Reflection helpers
        // ----------------------------

        private static boolean tryInvokeCommon(Object target, String id, World world, PlayerRef playerRef, Player player) {
            if (target == null) return false;

            // Common guesses for “run interaction by id”
            final String[] names = new String[] {
                    "executeInteraction",
                    "runInteraction",
                    "startInteraction",
                    "triggerInteraction",
                    "fireInteraction",
                    "beginInteraction",
                    "executeRootInteraction",
                    "runRootInteraction",
                    "startRootInteraction",
                    "triggerRootInteraction",
                    "fireRootInteraction",
                    "beginRootInteraction",
                    "interact",
                    "invokeInteraction"
            };

            // Try a bunch of argument shapes in order of “most likely to exist”
            final Object[][] argSets = new Object[][] {
                    new Object[] { id },
                    new Object[] { id, playerRef },
                    new Object[] { id, player },
                    new Object[] { id, world },
                    new Object[] { id, world, playerRef },
                    new Object[] { id, world, player },
                    new Object[] { id, playerRef, player },
                    new Object[] { id, world, playerRef, player }
            };

            for (String methodName : names) {
                for (Object[] args : argSets) {
                    if (tryInvokeByAssignableSignature(target, methodName, args)) {
                        return true;
                    }
                }
            }

            return false;
        }

        private static boolean tryInvokeByAssignableSignature(Object target, String methodName, Object[] args) {
            try {
                Method[] methods = target.getClass().getMethods();
                for (Method m : methods) {
                    if (!m.getName().equals(methodName)) continue;

                    Class<?>[] p = m.getParameterTypes();
                    if (p.length != args.length) continue;

                    boolean ok = true;
                    for (int i = 0; i < p.length; i++) {
                        Object a = args[i];
                        if (a == null) { ok = false; break; }
                        if (!p[i].isAssignableFrom(a.getClass())) { ok = false; break; }
                    }
                    if (!ok) continue;

                    m.setAccessible(true);
                    Object ret = m.invoke(target, args);

                    if (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) {
                        return (ret instanceof Boolean) && (Boolean) ret;
                    }
                    return true;
                }
            } catch (Throwable ignored) {}

            return false;
        }

        private static Object invokeNoArgs(Object target, String name) {
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
