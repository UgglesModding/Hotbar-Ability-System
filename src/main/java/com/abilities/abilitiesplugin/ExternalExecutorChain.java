package com.abilities.abilitiesplugin;

import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ExternalExecutorChain {

    private final List<Entry> entries = new ArrayList<>();

    public ExternalExecutorChain() {}

    public void discover() {
        entries.clear();

        PluginManager.get().getPlugins().forEach(plugin -> {
            if (plugin == null) return;

            // Optional: skip Hypixel internal spam
            String cn = plugin.getClass().getName();
            if (cn.startsWith("com.hypixel.")) return;

            for (Method m : plugin.getClass().getMethods()) {
                if (!m.getName().equals("doAbility")) continue;
                if (m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) continue;

                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2) continue;

                // Accept (Object,Object) always
                boolean objectObject =
                        (p[0] == Object.class && p[1] == Object.class);

                // Accept typed (PackagedAbilityData, AbilityContext) by NAME (no hard reference required)
                boolean typedNames =
                        "com.abilities.abilitiesplugin.PackagedAbilityData".equals(p[0].getName()) &&
                                "com.abilities.abilitiesplugin.AbilityContext".equals(p[1].getName());

                if (!objectObject && !typedNames) continue;

                try {
                    m.setAccessible(true);
                    entries.add(new Entry(plugin, m, objectObject ? Sig.OBJECT_OBJECT : Sig.TYPED));
                    System.out.println("[HCA] External ability hook: " + cn + "." + m.getName() +
                            "(" + p[0].getSimpleName() + "," + p[1].getSimpleName() + ")");
                } catch (Throwable ignored) {}
            }
        });

        System.out.println("[HCA] External ability hooks discovered=" + entries.size());
    }

    public boolean tryExecute(PackagedAbilityData data, AbilityContext ctx) {
        if (data == null || ctx == null) return false;

        for (Entry e : entries) {
            try {
                Object ret;

                // For BOTH signatures we pass the same objects.
                // (Object,Object) obviously works.
                // (PackagedAbilityData, AbilityContext) also works if the plugin compiled against HCA jar.
                ret = e.method.invoke(e.pluginInstance, data, ctx);

                if (ret instanceof Boolean && (Boolean) ret) {
                    return true;
                }
            } catch (Throwable ignored) {
                // swallow so one broken mod doesn't kill the chain
            }
        }

        return false;
    }

    private enum Sig { OBJECT_OBJECT, TYPED }

    private static final class Entry {
        final Object pluginInstance;
        final Method method;
        final Sig sig;

        Entry(Object pluginInstance, Method method, Sig sig) {
            this.pluginInstance = pluginInstance;
            this.method = method;
            this.sig = sig;
        }
    }
}
