package com.abilities.abilitiesplugin;

import java.util.List;

public class WeaponItemData {
    // Your item JSON uses:
    // { "ItemId": "CAO_Weapon", "AbilitySlots": [ { "Key": "Ability_DaggerLeap" } ] }
    public String ItemId;

    public List<WeaponAbilitySlot> AbilitySlots;

    // Optional if later you want to read engine inheritance (Parent field exists on many items)
    public String Parent;
}
