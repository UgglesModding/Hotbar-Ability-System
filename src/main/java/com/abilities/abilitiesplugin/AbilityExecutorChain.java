package com.abilities.abilitiesplugin;

import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;

public final class AbilityExecutorChain {

    private final List<IAbilityExecutor> executors = new ArrayList<>();

    public void discoverExecutors() {
        executors.clear();

        for (var plugin : PluginManager.get().getPlugins()) {
            if (plugin instanceof IAbilityExecutor exec) {
                executors.add(exec);
                System.out.println("[HCA] Found ability executor: " + plugin.getName()
                        + " class=" + plugin.getClass().getName());
            }
        }

        System.out.println("[HCA] Executors discovered=" + executors.size());
    }

    public boolean execute(AbilityContext ctx) {
        for (IAbilityExecutor exec : executors) {
            try {
                if (exec.doAbility(ctx)) return true;
            } catch (Throwable t) {
                System.out.println("[HCA] Executor crashed: " + exec.getClass().getName() + " err=" + t);
            }
        }
        return false;
    }
}
