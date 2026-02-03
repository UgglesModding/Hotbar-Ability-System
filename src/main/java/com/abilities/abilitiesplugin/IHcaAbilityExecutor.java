package com.abilities.abilitiesplugin;

public interface IHcaAbilityExecutor {
    /**
     * Return true if you handled the ability (stop the chain).
     * Return false to let the next plugin try.
     */
    boolean doAbility(AbilityContext ctx);
}
