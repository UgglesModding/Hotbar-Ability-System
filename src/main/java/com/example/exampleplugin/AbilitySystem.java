package com.example.exampleplugin;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;

public class AbilitySystem {

    private final AbilityRegistry registry;
    private final AbilityHotbarState state;

    public AbilitySystem(AbilityRegistry registry, AbilityHotbarState state) {
        this.registry = registry;
        this.state = state;
    }

    public AbilityRegistry getRegistry() {
        return registry;
    }

    public void openAbilityBar(PlayerRef playerRef, String abilityBarId) {
        var s = state.get(playerRef.getUsername());

        // If nothing passed, DO NOT enable and DO NOT touch hotbar data
        if (abilityBarId == null || abilityBarId.isBlank()) {
            s.enabled = false;
            return;
        }

        // Load if changed or not set yet
        if (s.currentAbilityBarId == null || !abilityBarId.equals(s.currentAbilityBarId)) {
            loadNewAbilityBar(playerRef, abilityBarId);
        }

        s.enabled = true;
    }

    public void loadNewAbilityBar(PlayerRef playerRef, String abilityBarId) {
        var s = state.get(playerRef.getUsername());

        if (abilityBarId == null || abilityBarId.isBlank()) {
            return;
        }

        AbilityBarData bar = registry.getBar(abilityBarId);
        if (bar == null || bar.Abilities == null) {
            // Bar missing -> fill empty safely
            s.currentAbilityBarId = abilityBarId;
            s.selectedAbilitySlot = 1;
            s.fillAllEmpty();
            return;
        }

        s.currentAbilityBarId = abilityBarId;
        s.selectedAbilitySlot = 1;

        // IMPORTANT:
        // bar.Abilities MUST now be a list of ITEM ASSET KEYS (strings),
        // e.g. "Items/U_Abilities/Ability_DaggerLeap"
        List<String> src = bar.Abilities;

        for (int i = 0; i < 9; i++) {
            String itemId = (i < src.size()) ? src.get(i) : null;

            if (itemId == null || itemId.isBlank()) {
                itemId = AbilityRegistry.EMPTY_ITEM_ID;
            }

            s.hotbarItemIds[i] = itemId;
        }

    }

    public void useSlot(PlayerRef playerRef, int slot0to9) {
        var s = state.get(playerRef.getUsername());

        int resolved = (slot0to9 == 0) ? s.selectedAbilitySlot : slot0to9;
        if (resolved < 1 || resolved > 9) return;

        // selecting a slot also updates selected
        if (slot0to9 != 0) {
            s.selectedAbilitySlot = resolved;
        }

        String itemAsset = s.hotbarItemIds[resolved - 1];
        if (itemAsset == null || itemAsset.isBlank()) {
            itemAsset = AbilityRegistry.EMPTY_ITEM_ID;
        }

        // This is where your RootInteraction execution will go later.
        // For now, log what item would execute.
        System.out.println("USE ABILITY ITEM: " + itemAsset + " for " + playerRef.getUsername());

        // TODO:
        // - resolve the item by ItemAsset
        // - execute its RootInteraction
    }

    public void closeAbilityBar(PlayerRef playerRef) {
        var s = state.get(playerRef.getUsername());
        s.enabled = false;
        s.currentAbilityBarId = null;
        s.selectedAbilitySlot = 1;
        s.fillAllEmpty();
    }

}
