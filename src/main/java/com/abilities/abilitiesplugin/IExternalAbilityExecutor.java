package com.abilities.abilitiesplugin;

public interface IExternalAbilityExecutor {
    /**
     * Return true if you handled the ability.
     * Return false to let the next executor try.
     */
    boolean doAbility(PackagedAbilityData data, AbilityContext ctx);
}
