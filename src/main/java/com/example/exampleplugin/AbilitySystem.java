// AbilitySystem.java
package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

public class AbilitySystem {

    private final WeaponRegistry weaponRegistry;
    private final AbilityHotbarState state;
    private final AbilityInteractionExecutor interactionExecutor;

    public AbilitySystem(
            WeaponRegistry weaponRegistry,
            AbilityHotbarState state,
            AbilityInteractionExecutor interactionExecutor
    ) {
        this.weaponRegistry = weaponRegistry;
        this.state = state;
        this.interactionExecutor = interactionExecutor;
    }

    /** Called when Q opens the ability bar */
    public void refreshFromHeldWeapon(
            PlayerRef playerRef,
            Store<EntityStore> store,
            Ref<EntityStore> entityRef
    ) {
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

        List<WeaponAbilitySlot> slots = weaponRegistry.getAbilitySlots(heldItemId);

        for (int i = 0; i < 9; i++) {
            if (slots != null && i < slots.size()) {
                WeaponAbilitySlot slot = slots.get(i);

                String key = (slot == null) ? null : slot.Key;
                String root = (slot == null) ? null : slot.RootInteraction;

                if (key == null || key.isBlank()) key = AbilityRegistry.EMPTY_ITEM_ID;

                s.hotbarItemIds[i] = key;
                s.hotbarRootInteractions[i] = (root == null || root.isBlank()) ? null : root;
            } else {
                s.hotbarItemIds[i] = AbilityRegistry.EMPTY_ITEM_ID;
                s.hotbarRootInteractions[i] = null;
            }
        }

        s.selectedAbilitySlot = 1;

        playerRef.sendMessage(Message.raw(
                "[AbilityBar] Loaded from held=" + heldItemId +
                        " slot1=" + s.hotbarItemIds[0] +
                        " root1=" + s.hotbarRootInteractions[0]
        ));
    }

    /** Called when player presses 1–9 */
    public void useSlot(PlayerRef playerRef, int slot1to9) {
        var s = state.get(playerRef.getUsername());

        if (slot1to9 < 1 || slot1to9 > 9) return;

        s.selectedAbilitySlot = slot1to9;

        String itemId = s.hotbarItemIds[slot1to9 - 1];
        String root = s.hotbarRootInteractions[slot1to9 - 1];

        if (itemId == null || itemId.isBlank()) itemId = AbilityRegistry.EMPTY_ITEM_ID;

        playerRef.sendMessage(Message.raw(
                "[Ability] slot=" + slot1to9 +
                        " itemId=" + itemId +
                        " root=" + (root == null ? "null" : root)
        ));

        if (root == null || root.isBlank()) return;

        boolean ok = interactionExecutor.execute(root, playerRef);
        if (!ok) {
            playerRef.sendMessage(Message.raw("[Ability] No handler registered for: " + root));
        }
    }

    private static String getHeldItemId(Player player) {
        ItemContainer hotbar = player.getInventory().getHotbar();
        byte active = player.getInventory().getActiveHotbarSlot();

        ItemStack stack = hotbar.getItemStack((short) active);
        if (stack == null) return null;

        return stack.getItemId();
    }
}
