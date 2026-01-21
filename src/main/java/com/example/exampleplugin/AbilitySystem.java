package com.example.exampleplugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;

public class AbilitySystem {

    private final AbilityRegistry registry;
    private final AbilityHotbarState state;
    private final AbilityInteractionExecutor executor; // ✅ MISSING FIELD (now added)

    // ✅ FIXED CONSTRUCTOR
    public AbilitySystem(
            AbilityRegistry registry,
            AbilityHotbarState state,
            AbilityInteractionExecutor executor
    ) {
        this.registry = registry;
        this.state = state;
        this.executor = executor;
    }

    public AbilityRegistry getRegistry() {
        return registry;
    }

    public void openAbilityBar(PlayerRef playerRef, String abilityBarId) {
        var s = state.get(playerRef.getUsername());

        if (abilityBarId == null || abilityBarId.isBlank()) {
            s.enabled = false;
            return;
        }

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
            s.currentAbilityBarId = abilityBarId;
            s.selectedAbilitySlot = 1;
            s.fillAllEmpty();
            return;
        }

        s.currentAbilityBarId = abilityBarId;
        s.selectedAbilitySlot = 1;

        List<String> src = bar.Abilities;

        for (int i = 0; i < 9; i++) {
            String itemId = (i < src.size()) ? normalizeItemId(src.get(i)) : null;

            if (itemId == null || itemId.isBlank()) {
                itemId = AbilityRegistry.EMPTY_ITEM_ID;
            }

            s.hotbarItemIds[i] = itemId;
        }
    }

    /**
     * slot0to9:
     * 1–9 = activate that slot
     * 0   = confirm → activate selectedAbilitySlot
     */
    public void useSlot(PlayerRef playerRef, int slot0to9) {
        var s = state.get(playerRef.getUsername());

        int resolved = (slot0to9 == 0) ? s.selectedAbilitySlot : slot0to9;
        if (resolved < 1 || resolved > 9) return;

        if (slot0to9 != 0) {
            s.selectedAbilitySlot = resolved;
        }

        String itemId = s.hotbarItemIds[resolved - 1];
        if (itemId == null || itemId.isBlank()) {
            itemId = AbilityRegistry.EMPTY_ITEM_ID;
        }

        AbilityData data = registry.getAbilityByItemId(itemId);

        String useInteraction = null;
        if (data != null && data.Interactions != null) {
            useInteraction = data.Interactions.Use;
        }

        System.out.println("[AbilitySystem] slot=" + resolved +
                " itemId=" + itemId +
                " ability=" + (data == null ? "null" : data.ID) +
                " Use=" + useInteraction);

        playerRef.sendMessage(Message.raw(
                "[Ability] slot=" + resolved +
                        " itemId=" + itemId +
                        " Use=" + useInteraction
        ));

        if (useInteraction == null || useInteraction.isBlank()) {
            return;
        }

        // ✅ EXECUTE INTERACTION
        boolean ok = executor.execute(useInteraction, playerRef);
        if (!ok) {
            playerRef.sendMessage(Message.raw(
                    "[Ability] No handler registered for interaction: " + useInteraction
            ));
        }
    }

    public void closeAbilityBar(PlayerRef playerRef) {
        var s = state.get(playerRef.getUsername());
        s.enabled = false;
        s.currentAbilityBarId = null;
        s.selectedAbilitySlot = 1;
        s.fillAllEmpty();
    }

    private static String normalizeItemId(String s) {
        if (s == null) return null;

        s = s.trim();
        if (s.isEmpty()) return null;

        s = s.replace('\\', '/');

        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }

        if (s.endsWith(".json")) {
            s = s.substring(0, s.length() - 5);
        }

        return s;
    }
}
