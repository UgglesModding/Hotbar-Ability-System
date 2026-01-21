package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

public class AbilitySystem {

    private final AbilityRegistry abilityRegistry;
    private final WeaponRegistry weaponRegistry;
    private final AbilityHotbarState state;

    public AbilitySystem(AbilityRegistry abilityRegistry, WeaponRegistry weaponRegistry, AbilityHotbarState state) {
        this.abilityRegistry = abilityRegistry;
        this.weaponRegistry = weaponRegistry;
        this.state = state;
    }

    public AbilityRegistry getRegistry() {
        return abilityRegistry;
    }

    /** Overwrite hotbar slots directly (1..9), padding with EMPTY. */
    public void setSlots(PlayerRef playerRef, List<String> itemIdsOrPaths) {
        var s = state.get(playerRef.getUsername());

        for (int i = 0; i < 9; i++) {
            String v = (itemIdsOrPaths != null && i < itemIdsOrPaths.size()) ? itemIdsOrPaths.get(i) : null;
            v = ItemIdUtil.normalizeItemId(v);

            if (v == null || v.isBlank()) v = AbilityRegistry.EMPTY_ITEM_ID;

            s.hotbarItemIds[i] = v;
        }

        s.selectedAbilitySlot = 1;

        System.out.println("[AbilitySystem] setSlots user=" + playerRef.getUsername()
                + " slot1=" + s.hotbarItemIds[0]
                + " slot2=" + s.hotbarItemIds[1]);
    }

    /** Call this when enabling the HUD (Q) to pull AbilitySlots from currently held weapon. */
    public void refreshFromHeldWeapon(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        var s = state.get(playerRef.getUsername());

        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            s.fillAllEmpty();
            return;
        }

        String heldItemId = getHeldItemId(player);
        if (heldItemId == null || heldItemId.isBlank()) {
            s.fillAllEmpty();
            return;
        }

        List<String> keys = weaponRegistry.resolveAbilityKeys(heldItemId);

        for (int i = 0; i < 9; i++) {
            String key = (keys != null && i < keys.size()) ? keys.get(i) : null;
            key = ItemIdUtil.normalizeItemId(key);

            if (key == null || key.isBlank()) key = AbilityRegistry.EMPTY_ITEM_ID;

            s.hotbarItemIds[i] = key;
        }

        s.selectedAbilitySlot = 1;

        System.out.println("[AbilitySystem] refreshFromHeldWeapon held=" + heldItemId
                + " slot1=" + s.hotbarItemIds[0]
                + " slot2=" + s.hotbarItemIds[1]
                + " slot3=" + s.hotbarItemIds[2]);
    }

    public void useSlot(PlayerRef playerRef, int slot1to9) {
        var s = state.get(playerRef.getUsername());

        if (slot1to9 < 1 || slot1to9 > 9) return;

        s.selectedAbilitySlot = slot1to9;

        String itemId = s.hotbarItemIds[slot1to9 - 1];
        if (itemId == null || itemId.isBlank()) itemId = AbilityRegistry.EMPTY_ITEM_ID;

        // Resolve ability json from item id (Ability_DaggerLeap -> AbilityData)
        AbilityData ability = abilityRegistry.getAbilityByItemId(itemId);

        String useInteraction = null;
        if (ability != null && ability.Interactions != null) {
            useInteraction = ability.Interactions.Use; // (assuming your AbilityData has Interactions.Use)
        }

        System.out.println("[AbilitySystem] USE slot=" + slot1to9
                + " itemId=" + itemId
                + " abilityJson=" + (ability == null ? "null" : ability.ID)
                + " Use=" + useInteraction);
    }

    private static String getHeldItemId(Player player) {
        ItemContainer hotbar = player.getInventory().getHotbar();
        byte active = player.getInventory().getActiveHotbarSlot(); // 0..8

        // ✅ Correct getter name
        ItemStack stack = hotbar.getItemStack((short) active); // :contentReference[oaicite:1]{index=1}
        if (stack == null) return null;

        return stack.getItemId();
    }
}
