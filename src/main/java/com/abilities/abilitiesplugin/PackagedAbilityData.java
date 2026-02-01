package com.abilities.abilitiesplugin;

public final class PackagedAbilityData {

    public final int Slot0to8;
    public final int Slot1to9;

    public final String Key;
    public final String ID;
    public final int MaxUses;
    public final float PowerMultiplier;
    public final boolean Consume;

    public final int AbilityValue;

    public final String RootInteraction;
    public final int RemainingUses;

    public PackagedAbilityData(
            int slot0to8,
            String Key,
            String ID,
            int MaxUses,
            float PowerMultiplier,
            int AbilityValue,
            String RootInteraction,
            int RemainingUses,
            boolean Consume
    ) {
        this.Slot0to8 = slot0to8;
        this.Slot1to9 = slot0to8 + 1;

        this.Key = Key;
        this.ID = ID;
        this.MaxUses = MaxUses;
        this.PowerMultiplier = PowerMultiplier;
        this.AbilityValue = AbilityValue;

        this.RootInteraction = RootInteraction;
        this.RemainingUses = RemainingUses;

        this.Consume = Consume;
    }
}
