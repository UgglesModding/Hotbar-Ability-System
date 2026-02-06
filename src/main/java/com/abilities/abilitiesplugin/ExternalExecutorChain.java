package com.abilities.abilitiesplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class ExternalExecutorChain {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final class Entry {
        final Object instance;
        final Method method;

        Entry(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int lastPluginCount = -1;

    public void discover() {
        entries.clear();

        List<PluginBase> plugins = PluginManager.get().getPlugins();
        if (plugins == null) {
            LOGGER.atInfo().log("[HCA] External ability executors discovered: 0 (no plugins list)");
            return;
        }

        for (PluginBase plugin : plugins) {
            if (plugin == null) continue;

            String cn = plugin.getClass().getName();

            // IMPORTANT:
            // - In production jar: skip scanning HCA itself
            // - In dev (classes folder): allow scanning HCA itself for testing
            if (cn.startsWith("com.abilities.abilitiesplugin")) {
                if (!isRunningFromDirectory(plugin)) {
                    continue;
                }
            }

            scanPlugin(plugin);
        }

        LOGGER.atInfo().log("[HCA] External ability executors discovered: %d", entries.size());
    }

    public boolean tryExecute(PackagedAbilityData data, AbilityContext ctx) {
        if (data == null || ctx == null) return false;

        refreshIfNeeded();

        for (Entry e : entries) {
            try {
                Object result = e.method.invoke(e.instance, data, ctx);
                if (result instanceof Boolean && (Boolean) result) return true;
            } catch (Throwable t) {
                LOGGER.atSevere().log(
                        "[HCA] External ability error in %s.%s : %s",
                        e.instance.getClass().getName(),
                        e.method.getName(),
                        String.valueOf(t.getMessage())
                );
            }
        }

        return false;
    }

    private void refreshIfNeeded() {
        List<PluginBase> plugins = PluginManager.get().getPlugins();
        int now = (plugins == null) ? 0 : plugins.size();
        if (now != lastPluginCount) {
            lastPluginCount = now;
            discover();
        }
    }

    private void scanPlugin(Object plugin) {
        Class<?> cls = plugin.getClass();

        for (Method m : cls.getDeclaredMethods()) {
            if (!m.getName().equals("doAbility")) continue;
            if (!boolean.class.equals(m.getReturnType())) continue;

            Class<?>[] params = m.getParameterTypes();
            if (params.length != 2) continue;

            boolean typed = (params[0] == PackagedAbilityData.class && params[1] == AbilityContext.class);
            boolean generic = (params[0] == Object.class && params[1] == Object.class);

            if (!typed && !generic) continue;

            m.setAccessible(true);
            entries.add(new Entry(plugin, m));

            LOGGER.atInfo().log(
                    "[HCA] External ability hook (%s): %s.%s",
                    typed ? "typed" : "generic",
                    cls.getName(),
                    m.getName()
            );
        }
    }

    private boolean isRunningFromDirectory(Object plugin) {
        try {
            var cs = plugin.getClass().getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) return false;
            URI uri = cs.getLocation().toURI();
            Path p = Paths.get(uri);
            return Files.isDirectory(p);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
