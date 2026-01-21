package com.example.exampleplugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;

public class AbilitySystem {

    private final AbilityRegistry registry;
    private final AbilityHotbarState state;
    private final AbilityInteractionExecutor executor;

    public AbilitySystem(AbilityRegistry registry, AbilityHotbarState state, AbilityInteractionExecutor executor) {
        this.registry = registry;
        this.state = state;
        this.executor = executor;
    }

    public AbilityRegistry getRegistry() {
        return registry;
    }

    public void enable(PlayerRef playerRef) {
        var s = state.get(playerRef.getUsername());
        s.enabled = true;
    }

    public void disable(PlayerRef playerRef) {
        var s = state.get(playerRef.getUsername());
        s.enabled = false;
    }

    /**
     * For now: let commands (and later weapon parsing) set the 1..9 ability items.
     * Accepts either:
     *  - "Ability_DaggerLeap"
     *  - "Items/U_Abilities/Ability_DaggerLeap"
     */
    public void setSlots(PlayerRef playerRef, List<String> itemIdsOrAssets) {
        var s = state.get(playerRef.getUsername());

        for (int i = 0; i < 9; i++) {
            String v = (itemIdsOrAssets != null && i < itemIdsOrAssets.size()) ? itemIdsOrAssets.get(i) : null;
            String itemId = normalizeItemId(v);

            if (itemId == null || itemId.isBlank()) {
                itemId = AbilityRegistry.EMPTY_ITEM_ID;
            }

            s.hotbarItemIds[i] = itemId;
        }
    }

    /**
     * slot0to9:
     *  - 1..9 => use that slot
     *  - 0    => confirm (uses selectedAbilitySlot)
     */
    public void useSlot(PlayerRef playerRef, int slot0to9) {
        var s = state.get(playerRef.getUsername());

        int resolved = (slot0to9 == 0) ? s.selectedAbilitySlot : slot0to9;
        if (resolved < 1 || resolved > 9) return;

        if (slot0to9 != 0) {
            s.selectedAbilitySlot = resolved;
        }

        String itemId = s.hotbarItemIds[resolved - 1];
        if (itemId == null || itemId.isBlank()) itemId = AbilityRegistry.EMPTY_ITEM_ID;

        // Resolve ability data from itemId
        AbilityData data = registry.getAbilityByItemId(itemId);

        if (data == null) {
            playerRef.sendMessage(Message.raw("[Ability] No AbilityData for itemId=" + itemId));
            return;
        }

        String useInteraction = (data.Interactions != null) ? data.Interactions.Use : null;

        playerRef.sendMessage(Message.raw(
                "[Ability] slot=" + resolved + " itemId=" + itemId + " Use=" + useInteraction
        ));

        boolean ok = executor.execute(useInteraction, playerRef);
        if (!ok) {
            playerRef.sendMessage(Message.raw("[Ability] Handler missing for: " + useInteraction));
        }
    }

    /**
     * Normalizes:
     * - "Ability_DaggerLeap" -> "Ability_DaggerLeap"
     * - "Items/U_Abilities/Ability_DaggerLeap" -> "Ability_DaggerLeap"
     * - "Items\\U_Abilities\\Ability_DaggerLeap" -> "Ability_DaggerLeap"
     * - "Ability_DaggerLeap.json" -> "Ability_DaggerLeap"
     */
    public static String normalizeItemId(String s) {
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
