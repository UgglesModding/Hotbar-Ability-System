package com.abilities.abilitiesplugin;

public final class PackagedAbilityData {
    public final int SlotIndex;   // 0..8
    public final int Slot0to8;    // same as SlotIndex
    public final int Slot1to9;    // 1..9

    public final String Key;
    public final String ID;
    public final int MaxUses;
    public final float PowerMultiplier;
    public final boolean Consume;

    public final int AbilityValue;

    public final String RootInteraction;
    public final int RemainingUses;

    // New "full" ctor
    public PackagedAbilityData(
            int slotIndex,
            int slot0to8,
            String key,
            String id,
            int maxUses,
            float powerMultiplier,
            int abilityValue,
            String rootInteraction,
            int remainingUses,
            boolean consume
    ) {
        this.SlotIndex = slotIndex;
        this.Slot0to8 = slot0to8;
        this.Slot1to9 = slot0to8 + 1;

        this.Key = key;
        this.ID = id;
        this.MaxUses = maxUses;
        this.PowerMultiplier = powerMultiplier;
        this.AbilityValue = abilityValue;

        this.RootInteraction = rootInteraction;
        this.RemainingUses = remainingUses;

        this.Consume = consume;
    }

    // Compatibility ctor (older callsites that only pass slotIndex once)
    public PackagedAbilityData(
            int slotIndex,
            String key,
            String id,
            int maxUses,
            float powerMultiplier,
            int abilityValue,
            String rootInteraction,
            int remainingUses,
            boolean consume
    ) {
        this(slotIndex, slotIndex, key, id, maxUses, powerMultiplier, abilityValue, rootInteraction, remainingUses, consume);
    }
}
