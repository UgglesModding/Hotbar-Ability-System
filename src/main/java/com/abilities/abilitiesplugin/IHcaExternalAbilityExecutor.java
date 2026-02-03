package com.abilities.abilitiesplugin;

public interface IHcaExternalAbilityExecutor {
    /**
     * Return true if you handled the ability.
     * Return false to let the next executor try.
     */
    boolean doAbility(PackagedAbilityData data, AbilityContext ctx);
}
