package com.abilities.abilitiesplugin;

import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;

public final class HcaExternalExecutorChain {

    private final List<IHcaExternalAbilityExecutor> executors = new ArrayList<>();

    public HcaExternalExecutorChain() {}

    public void discover() {
        executors.clear();

        PluginManager.get().getPlugins().forEach(plugin -> {
            // If the plugin itself implements the interface, register it.
            if (plugin instanceof IHcaExternalAbilityExecutor exec) {
                executors.add(exec);
                System.out.println("[HCA] External executor found (plugin): " + plugin.getName());
                return;
            }

            // Or: scan plugin classes for any IHcaExternalAbilityExecutor implementations
            // (OPTIONAL - only if you want separate executor classes)
        });

        System.out.println("[HCA] External executors registered=" + executors.size());
    }

    public boolean tryExecute(PackagedAbilityData data, AbilityContext ctx) {
        for (IHcaExternalAbilityExecutor ex : executors) {
            try {
                if (ex != null && ex.doAbility(data, ctx)) return true;
            } catch (Throwable t) {
                System.out.println("[HCA] External executor threw: " + t.getMessage());
            }
        }
        return false;
    }
}
