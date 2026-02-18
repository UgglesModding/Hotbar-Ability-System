package com.abilities.abilitiesplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AbilityInteractionExecutor {

    public boolean execute(String interactionName, PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref, World world) {
        if (interactionName == null || interactionName.isBlank()) return false;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return false;

        boolean ran = RootInteractionRunner.tryExecute(world, playerRef, player, store, ref, interactionName);
        if (!ran) {
            System.out.println("[HCA] Root interaction failed: " + interactionName);
            playerRef.sendMessage(Message.raw(
                    "[HCA] Could not execute root interaction '" + interactionName + "'."
            ));
            for (String line : RootInteractionRunner.debugDescribeAvailableExecutors(world, playerRef, player, store, ref)) {
                System.out.println(line);
                playerRef.sendMessage(Message.raw(line));
            }
        }

        return ran;
    }

    public static final class RootInteractionRunner {
        private static volatile String lastDirectPathError;
        private static final String[] EXECUTOR_METHODS = new String[] {
                "executeRootInteraction",
                "runRootInteraction",
                "triggerRootInteraction",
                "performRootInteraction",
                "activateRootInteraction",
                "executeRoot",
                "runRoot",
                "triggerRoot",
                "activateRoot",
                "executeInteraction",
                "runInteraction",
                "triggerInteraction",
                "performInteraction"
        };
        private static final String[] EXECUTOR_PREFIXES = new String[] {
                "execute",
                "run",
                "trigger",
                "perform",
                "activate",
                "apply"
        };

        public static boolean tryExecute(
                World world,
                PlayerRef playerRef,
                Player player,
                Store<EntityStore> store,
                Ref<EntityStore> ref,
                String rootName
        ) {
            if (rootName == null || rootName.isBlank()) return false;

            if (tryViaInteractionContext(playerRef, store, ref, rootName)) return true;

            for (Object target : collectTargets(world, playerRef, player, store)) {
                if (tryOnTarget(target, world, playerRef, player, store, ref, rootName)) return true;
            }

            return false;
        }

        public static List<String> debugDescribeAvailableExecutors(
                World world,
                PlayerRef playerRef,
                Player player,
                Store<EntityStore> store,
                Ref<EntityStore> ref
        ) {
            List<String> lines = new ArrayList<>();
            lines.add("[HCA] Interaction debug (callable matches):");
            if (lastDirectPathError != null && !lastDirectPathError.isBlank()) {
                lines.add("[HCA] direct-path-error: " + lastDirectPathError);
            }

            int count = 0;
            List<Object> targets = collectTargets(world, playerRef, player, store);
            int scannedClasses = 0;

            for (Object target : targets) {
                if (target == null) continue;
                scannedClasses++;

                for (Method m : allMethods(target.getClass())) {
                    String methodName = m.getName().toLowerCase();
                    boolean nameMatch = methodName.contains("interaction") || methodName.contains("root");
                    if (!nameMatch) {
                        for (String prefix : EXECUTOR_PREFIXES) {
                            if (methodName.startsWith(prefix)) {
                                nameMatch = true;
                                break;
                            }
                        }
                    }
                    if (!nameMatch) continue;

                    String sig = buildSignature(target.getClass(), m);
                    if (canBuildArgs(m.getParameterTypes(), world, playerRef, player, store, ref)) {
                        lines.add("[HCA] " + sig);
                        count++;
                        if (count >= 8) return lines;
                    }
                }
            }

            if (count == 0) {
                lines.add("[HCA] no callable root/interaction methods found.");
            }

            lines.add("[HCA] targets scanned=" + targets.size() + ", classes=" + scannedClasses);

            int classLines = 0;
            for (Object target : targets) {
                if (target == null) continue;
                lines.add("[HCA] target: " + target.getClass().getName());
                classLines++;
                if (classLines >= 8) break;
            }

            int candidateLines = 0;
            for (Object target : targets) {
                if (target == null) continue;
                for (Method m : allMethods(target.getClass())) {
                    String methodName = m.getName().toLowerCase();
                    boolean nameMatch = methodName.contains("interaction") || methodName.contains("root");
                    if (!nameMatch) {
                        for (String prefix : EXECUTOR_PREFIXES) {
                            if (methodName.startsWith(prefix)) {
                                nameMatch = true;
                                break;
                            }
                        }
                    }
                    if (!nameMatch) continue;

                    lines.add("[HCA] candidate: " + buildSignature(target.getClass(), m));
                    candidateLines++;
                    if (candidateLines >= 10) return lines;
                }
            }

            return lines;
        }

        private static boolean tryOnTarget(
                Object target,
                World world,
                PlayerRef playerRef,
                Player player,
                Store<EntityStore> store,
                Ref<EntityStore> ref,
                String rootName
        ) {
            if (target == null) return false;

            if (tryExecutorMethods(target, world, playerRef, player, store, ref, rootName)) return true;

            Class<?> c = target.getClass();
            for (Method getter : allMethods(c)) {
                if (getter.getParameterCount() != 0) continue;

                String name = getter.getName().toLowerCase();
                if (!name.contains("interaction") && !name.contains("root")) continue;

                try {
                    getter.setAccessible(true);
                    Object nested = getter.invoke(target);
                    if (nested == null || nested == target) continue;

                    if (tryExecutorMethods(nested, world, playerRef, player, store, ref, rootName)) return true;
                } catch (Throwable ignored) {}
            }

            return false;
        }

        private static boolean tryExecutorMethods(
                Object target,
                World world,
                PlayerRef playerRef,
                Player player,
                Store<EntityStore> store,
                Ref<EntityStore> ref,
                String rootName
        ) {
            for (String methodName : EXECUTOR_METHODS) {
                for (Method m : allMethods(target.getClass())) {
                    if (!m.getName().equals(methodName)) continue;

                    Object[] args = buildArgs(m.getParameterTypes(), world, playerRef, player, store, ref, rootName);
                    if (args == null) continue;

                    try {
                        m.setAccessible(true);
                        Object result = m.invoke(target, args);
                        if (result instanceof Boolean b) return b;
                        return true;
                    } catch (Throwable ignored) {}
                }
            }

            for (Method m : allMethods(target.getClass())) {
                String methodName = m.getName().toLowerCase();
                boolean nameMatch = methodName.contains("interaction") || methodName.contains("root");
                if (!nameMatch) {
                    for (String prefix : EXECUTOR_PREFIXES) {
                        if (methodName.startsWith(prefix)) {
                            nameMatch = true;
                            break;
                        }
                    }
                }
                if (!nameMatch) continue;

                Object[] args = buildArgs(m.getParameterTypes(), world, playerRef, player, store, ref, rootName);
                if (args == null) continue;

                try {
                    m.setAccessible(true);
                    Object result = m.invoke(target, args);
                    if (result instanceof Boolean b) return b;
                    return true;
                } catch (Throwable ignored) {}
            }

            return false;
        }

        private static Object[] buildArgs(
                Class<?>[] paramTypes,
                World world,
                PlayerRef playerRef,
                Player player,
                Store<EntityStore> store,
                Ref<EntityStore> ref,
                String rootName
        ) {
            Object[] args = new Object[paramTypes.length];
            boolean passedRootName = false;

            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> p = paramTypes[i];

                if (p == String.class || p == CharSequence.class) {
                    args[i] = rootName;
                    passedRootName = true;
                    continue;
                }
                if (p.isEnum()) {
                    Object enumValue = findEnumConstant((Class<? extends Enum<?>>) p, rootName);
                    if (enumValue == null) return null;
                    args[i] = enumValue;
                    passedRootName = true;
                    continue;
                }
                Object typedName = tryConvertNameType(p, rootName);
                if (typedName != null) {
                    args[i] = typedName;
                    passedRootName = true;
                    continue;
                }
                if (world != null && p.isAssignableFrom(world.getClass())) {
                    args[i] = world;
                    continue;
                }
                if (playerRef != null && p.isAssignableFrom(playerRef.getClass())) {
                    args[i] = playerRef;
                    continue;
                }
                if (player != null && p.isAssignableFrom(player.getClass())) {
                    args[i] = player;
                    continue;
                }
                if (store != null && p.isAssignableFrom(store.getClass())) {
                    args[i] = store;
                    continue;
                }
                if (ref != null && p.isAssignableFrom(ref.getClass())) {
                    args[i] = ref;
                    continue;
                }
                if (p == boolean.class || p == Boolean.class) {
                    args[i] = false;
                    continue;
                }
                if (p == int.class || p == Integer.class) {
                    args[i] = 0;
                    continue;
                }
                if (p == long.class || p == Long.class) {
                    args[i] = 0L;
                    continue;
                }
                if (p == float.class || p == Float.class) {
                    args[i] = 0f;
                    continue;
                }
                if (p == double.class || p == Double.class) {
                    args[i] = 0d;
                    continue;
                }
                if (p == byte.class || p == Byte.class) {
                    args[i] = (byte) 0;
                    continue;
                }
                if (p == short.class || p == Short.class) {
                    args[i] = (short) 0;
                    continue;
                }
                if (p == char.class || p == Character.class) {
                    args[i] = '\0';
                    continue;
                }

                if (!p.isPrimitive()) {
                    args[i] = null;
                    continue;
                }

                return null;
            }

            if (!passedRootName) return null;
            return args;
        }

        private static Enum<?> findEnumConstant(Class<? extends Enum<?>> enumType, String value) {
            if (value == null || value.isBlank()) return null;
            String wanted = value.trim();

            for (Enum<?> e : enumType.getEnumConstants()) {
                if (e.name().equals(wanted)) return e;
            }
            for (Enum<?> e : enumType.getEnumConstants()) {
                if (e.name().equalsIgnoreCase(wanted)) return e;
            }

            String normalized = wanted.replace('-', '_').replace(' ', '_');
            for (Enum<?> e : enumType.getEnumConstants()) {
                if (e.name().equalsIgnoreCase(normalized)) return e;
            }

            return null;
        }

        private static Object tryConvertNameType(Class<?> paramType, String rootName) {
            if (paramType == String.class || paramType == CharSequence.class) return rootName;
            if (paramType == Object.class) return rootName;

            try {
                Method valueOf = paramType.getMethod("valueOf", String.class);
                if (Modifier.isStatic(valueOf.getModifiers()) && paramType.isAssignableFrom(valueOf.getReturnType())) {
                    return valueOf.invoke(null, rootName);
                }
            } catch (Throwable ignored) {}
            try {
                Method fromString = paramType.getMethod("fromString", String.class);
                if (Modifier.isStatic(fromString.getModifiers()) && paramType.isAssignableFrom(fromString.getReturnType())) {
                    return fromString.invoke(null, rootName);
                }
            } catch (Throwable ignored) {}
            try {
                Method parse = paramType.getMethod("parse", String.class);
                if (Modifier.isStatic(parse.getModifiers()) && paramType.isAssignableFrom(parse.getReturnType())) {
                    return parse.invoke(null, rootName);
                }
            } catch (Throwable ignored) {}
            try {
                Method of = paramType.getMethod("of", String.class);
                if (Modifier.isStatic(of.getModifiers()) && paramType.isAssignableFrom(of.getReturnType())) {
                    return of.invoke(null, rootName);
                }
            } catch (Throwable ignored) {}
            try {
                var ctor = paramType.getConstructor(String.class);
                return ctor.newInstance(rootName);
            } catch (Throwable ignored) {}

            return null;
        }

        private static boolean canBuildArgs(
                Class<?>[] paramTypes,
                World world,
                PlayerRef playerRef,
                Player player,
                Store<EntityStore> store,
                Ref<EntityStore> ref
        ) {
            Object[] args = buildArgs(paramTypes, world, playerRef, player, store, ref, "StaffFlight");
            return args != null;
        }

        private static String buildSignature(Class<?> owner, Method m) {
            StringBuilder sb = new StringBuilder();
            sb.append(owner.getSimpleName()).append(".").append(m.getName()).append("(");
            Class<?>[] p = m.getParameterTypes();
            for (int i = 0; i < p.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(p[i].getSimpleName());
            }
            sb.append(")");
            return sb.toString();
        }

        private static List<Object> collectTargets(
                World world,
                PlayerRef playerRef,
                Player player,
                Store<EntityStore> store
        ) {
            ArrayList<Object> out = new ArrayList<>();
            ArrayDeque<Object> queue = new ArrayDeque<>();
            Map<Object, Boolean> seen = new IdentityHashMap<>();

            addTarget(queue, seen, world);
            addTarget(queue, seen, playerRef);
            addTarget(queue, seen, player);
            addTarget(queue, seen, store);

            int depth = 0;
            int levelRemaining = queue.size();
            int nextLevel = 0;

            while (!queue.isEmpty() && out.size() < 24 && depth <= 2) {
                Object cur = queue.pollFirst();
                levelRemaining--;
                out.add(cur);

                for (Method getter : allMethods(cur.getClass())) {
                    if (getter.getParameterCount() != 0) continue;
                    if (getter.getReturnType() == void.class) continue;
                    if (getter.getReturnType().isPrimitive()) continue;
                    if (getter.getDeclaringClass() == Object.class) continue;
                    if (getter.getName().equals("getClass")) continue;

                    String n = getter.getName().toLowerCase();
                    boolean likelyService =
                            n.startsWith("get")
                                    || n.contains("executor")
                                    || n.contains("interaction")
                                    || n.contains("root")
                                    || n.contains("manager")
                                    || n.contains("module")
                                    || n.contains("service")
                                    || n.contains("system")
                                    || n.contains("item");
                    if (!likelyService) continue;

                    try {
                        getter.setAccessible(true);
                        Object nested = getter.invoke(cur);
                        if (nested == null) continue;
                        String pkg = nested.getClass().getPackageName();
                        if (pkg.startsWith("java.")) continue;
                        if (pkg.startsWith("javax.")) continue;

                        if (addTarget(queue, seen, nested)) nextLevel++;
                    } catch (Throwable ignored) {}
                }

                if (levelRemaining == 0) {
                    depth++;
                    levelRemaining = nextLevel;
                    nextLevel = 0;
                }
            }

            return out;
        }

        private static boolean addTarget(ArrayDeque<Object> queue, Map<Object, Boolean> seen, Object obj) {
            if (obj == null) return false;
            if (seen.containsKey(obj)) return false;
            seen.put(obj, Boolean.TRUE);
            queue.addLast(obj);
            return true;
        }

        @SuppressWarnings("unchecked")
        private static boolean tryViaInteractionContext(
                PlayerRef playerRef,
                Store<EntityStore> store,
                Ref<EntityStore> ref,
                String rootName
        ) {
            lastDirectPathError = null;
            if (tryViaRunSpecificCommand(playerRef, rootName)) return true;
            boolean ok = tryViaInteractionCommandInjection(playerRef, store, ref, rootName);
            if (!ok) {
                lastDirectPathError = "command-path failed (interaction slot mapping or command dispatch)";
            }
            return ok;
        }

        private static boolean tryViaRunSpecificCommand(PlayerRef playerRef, String rootName) {
            if (playerRef == null || rootName == null || rootName.isBlank()) return false;

            String[] types = new String[] { "Secondary", "Ability3", "Ability2", "Ability1", "Use", "Primary" };
            for (String type : types) {
                if (runCommandOnPlayerRef(playerRef, "interaction run specific " + type + " " + rootName)) {
                    return true;
                }
            }

            return false;
        }

        @SuppressWarnings("unchecked")
        private static boolean tryViaHeldInteractionInjection(
                Store<EntityStore> store,
                Ref<EntityStore> ref,
                String rootName
        ) {
            try {
                Class<?> interactionModuleClass = Class.forName(
                        "com.hypixel.hytale.server.core.modules.interaction.InteractionModule"
                );
                Object interactionModule = interactionModuleClass.getMethod("get").invoke(null);
                if (interactionModule == null) return false;

                Class<?> interactionTypeClass = Class.forName("com.hypixel.hytale.protocol.InteractionType");
                Enum<?> injectedType =
                        findEnumConstant((Class<? extends Enum<?>>) interactionTypeClass, "Ability3");
                if (injectedType == null) {
                    injectedType = findEnumConstant((Class<? extends Enum<?>>) interactionTypeClass, "Ability2");
                }
                if (injectedType == null) {
                    injectedType = findEnumConstant((Class<? extends Enum<?>>) interactionTypeClass, "Ability1");
                }
                if (injectedType == null) return false;

                Object interactionsTypeObj = interactionModuleClass
                        .getMethod("getInteractionsComponentType")
                        .invoke(interactionModule);
                if (!(interactionsTypeObj instanceof ComponentType<?, ?> interactionsType)) return false;

                Object interactions = store.ensureAndGetComponent(ref, (ComponentType<EntityStore, ?>) interactionsType);
                if (interactions == null) return false;

                Method getInteractionId = interactions.getClass().getMethod("getInteractionId", interactionTypeClass);
                Method setInteractionId = interactions.getClass().getMethod("setInteractionId", interactionTypeClass, String.class);

                String previous = (String) getInteractionId.invoke(interactions, injectedType);
                setInteractionId.invoke(interactions, injectedType, rootName);

                Method takeBuffer = Store.class.getDeclaredMethod("takeCommandBuffer");
                Method storeBuffer = Store.class.getDeclaredMethod("storeCommandBuffer", Class.forName("com.hypixel.hytale.component.CommandBuffer"));
                takeBuffer.setAccessible(true);
                storeBuffer.setAccessible(true);

                Object commandBuffer = takeBuffer.invoke(store);
                if (commandBuffer == null) {
                    setInteractionId.invoke(interactions, injectedType, previous);
                    return false;
                }

                try {
                    Object managerTypeObj = interactionModuleClass
                            .getMethod("getInteractionManagerComponent")
                            .invoke(interactionModule);
                    if (!(managerTypeObj instanceof ComponentType<?, ?> managerType)) return false;

                    Object manager = store.getComponent(ref, (ComponentType<EntityStore, ?>) managerType);
                    if (manager == null) return false;

                    Class<?> commandBufferClass = Class.forName("com.hypixel.hytale.component.CommandBuffer");
                    Method tryRunHeld = manager.getClass().getMethod(
                            "tryRunHeldInteraction",
                            Ref.class,
                            commandBufferClass,
                            interactionTypeClass
                    );

                    tryRunHeld.invoke(manager, ref, commandBuffer, injectedType);
                    return true;
                } finally {
                    try {
                        setInteractionId.invoke(interactions, injectedType, previous);
                    } catch (Throwable ignored) {}
                    try {
                        storeBuffer.invoke(store, commandBuffer);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        private static boolean tryViaInteractionCommandInjection(
                PlayerRef playerRef,
                Store<EntityStore> store,
                Ref<EntityStore> ref,
                String rootName
        ) {
            if (playerRef == null) return false;

            try {
                Class<?> interactionModuleClass = Class.forName(
                        "com.hypixel.hytale.server.core.modules.interaction.InteractionModule"
                );
                Object interactionModule = interactionModuleClass.getMethod("get").invoke(null);
                if (interactionModule == null) return false;

                Class<?> interactionTypeClass = Class.forName("com.hypixel.hytale.protocol.InteractionType");
                Enum<?> injectedType =
                        findEnumConstant((Class<? extends Enum<?>>) interactionTypeClass, "Secondary");
                if (injectedType == null) injectedType = findEnumConstant((Class<? extends Enum<?>>) interactionTypeClass, "Ability3");
                if (injectedType == null) injectedType = findEnumConstant((Class<? extends Enum<?>>) interactionTypeClass, "Ability2");
                if (injectedType == null) injectedType = findEnumConstant((Class<? extends Enum<?>>) interactionTypeClass, "Ability1");
                if (injectedType == null) injectedType = findEnumConstant((Class<? extends Enum<?>>) interactionTypeClass, "Use");
                if (injectedType == null) return false;

                Object interactionsTypeObj = interactionModuleClass
                        .getMethod("getInteractionsComponentType")
                        .invoke(interactionModule);
                if (!(interactionsTypeObj instanceof ComponentType<?, ?> interactionsType)) return false;

                Object interactions = store.ensureAndGetComponent(ref, (ComponentType<EntityStore, ?>) interactionsType);
                if (interactions == null) return false;

                Method getInteractionId = interactions.getClass().getMethod("getInteractionId", interactionTypeClass);
                Method setInteractionId = interactions.getClass().getMethod("setInteractionId", interactionTypeClass, String.class);

                setInteractionId.invoke(interactions, injectedType, rootName);

                String now = (String) getInteractionId.invoke(interactions, injectedType);
                if (now == null || now.isBlank()) return false;

                return runCommandOnPlayerRef(playerRef, "interaction run " + ((Enum<?>) injectedType).name());
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static boolean runCommandOnPlayerRef(PlayerRef playerRef, String cmdNoSlash) {
            if (playerRef == null || cmdNoSlash == null || cmdNoSlash.isBlank()) return false;
            Object target = playerRef;
            Class<?> c = target.getClass();

            String[] methods = new String[] { "executeCommand", "runCommand", "dispatchCommand", "performCommand" };
            for (String m : methods) {
                try {
                    Method mm = c.getMethod(m, String.class);
                    mm.invoke(target, cmdNoSlash);
                    return true;
                } catch (Throwable ignored) {}
            }

            try {
                Method chat = c.getMethod("chat", String.class);
                chat.invoke(target, "/" + cmdNoSlash);
                return true;
            } catch (Throwable ignored) {}

            try {
                Method sendChat = c.getMethod("sendChatMessage", String.class);
                sendChat.invoke(target, "/" + cmdNoSlash);
                return true;
            } catch (Throwable ignored) {}

            return false;
        }

        private static List<Method> allMethods(Class<?> type) {
            ArrayList<Method> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            Class<?> c = type;
            while (c != null) {
                for (Method m : c.getDeclaredMethods()) {
                    String key = methodKey(m);
                    if (seen.add(key)) out.add(m);
                }
                c = c.getSuperclass();
            }

            for (Method m : type.getMethods()) {
                String key = methodKey(m);
                if (seen.add(key)) out.add(m);
            }

            return out;
        }

        private static String methodKey(Method m) {
            StringBuilder sb = new StringBuilder();
            sb.append(m.getName()).append("(");
            Class<?>[] p = m.getParameterTypes();
            for (int i = 0; i < p.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(p[i].getName());
            }
            sb.append(")");
            return sb.toString();
        }
    }
}
