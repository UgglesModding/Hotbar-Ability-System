package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

public class AbilitySystem {

    private final WeaponRegistry weaponRegistry;
    private final AbilityHotbarState state;

    // KEEP: your existing interaction executor system
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

        List<WeaponAbilitySlot> slots = weaponRegistry.getAbilitySlots(heldItemId);

        for (int i = 0; i < 9; i++) {
            if (slots != null && i < slots.size()) {
                WeaponAbilitySlot slot = slots.get(i);

                String key = (slot == null) ? null : slot.Key;
                if (key == null || key.isBlank()) key = AbilityRegistry.EMPTY_ITEM_ID;

                s.hotbarItemIds[i] = key;

                // KEEP: root interaction slot value (future-proof)
                s.hotbarRootInteractions[i] = (slot == null) ? null : slot.RootInteraction;

                // NEW: plugin fields
                s.hotbarAbilityIds[i] = (slot == null) ? null : slot.ID;
                s.hotbarPluginFlags[i] = slot != null && slot.Plugin;

            } else {
                s.hotbarItemIds[i] = AbilityRegistry.EMPTY_ITEM_ID;
                s.hotbarRootInteractions[i] = null;

                s.hotbarAbilityIds[i] = null;
                s.hotbarPluginFlags[i] = false;
            }
        }

        s.selectedAbilitySlot = 1;

        playerRef.sendMessage(Message.raw(
                "[AbilityBar] Loaded held=" + heldItemId +
                        " slot1Key=" + s.hotbarItemIds[0] +
                        " slot1Id=" + s.hotbarAbilityIds[0] +
                        " slot1Plugin=" + s.hotbarPluginFlags[0] +
                        " slot1Root=" + s.hotbarRootInteractions[0]
        ));
    }

    public void useSlot(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref, World world, int slot1to9) {
        var s = state.get(playerRef.getUsername());

        if (slot1to9 < 1 || slot1to9 > 9) return;
        s.selectedAbilitySlot = slot1to9;

        // NEW: plugin dispatch info
        boolean plugin = s.hotbarPluginFlags[slot1to9 - 1];
        String id = s.hotbarAbilityIds[slot1to9 - 1];

        // KEEP: root interaction info
        String root = s.hotbarRootInteractions[slot1to9 - 1];

        playerRef.sendMessage(Message.raw(
                "[Ability] slot=" + slot1to9 +
                        " plugin=" + plugin +
                        " id=" + (id == null ? "null" : id) +
                        " root=" + (root == null ? "null" : root)
        ));

        // If Plugin=true, do plugin pipeline first (for now ignore root execution)
        if (plugin) {
            if (id == null || id.isBlank()) {
                playerRef.sendMessage(Message.raw("[Ability] Plugin=true but ID missing."));
                return;
            }

            world.execute(() -> {
                AbilityContext ctx = AbilityContext.from(playerRef, store, ref, world);
                boolean handled = AbilityDispatch.dispatch(id, ctx);

                if (!handled) {
                    playerRef.sendMessage(Message.raw("[Ability] No plugin handled id=" + id));
                    // Optional: fall back to root path if you want later:
                    // tryExecuteRootFallback(playerRef, store, ref, world, root);
                }
            });

            return;
        }

        // Plugin=false: KEEP your existing root/interaction executor behavior
        // For now this can remain as "print + executor" until root execution exists.
        if (root == null || root.isBlank()) return;

        world.execute(() -> {
            // This is your existing executor pathway.
            boolean ok = interactionExecutor.execute(root, playerRef, store, ref, world);
            if (!ok) {
                playerRef.sendMessage(Message.raw("[Ability] No handler registered for root: " + root));
            }
        });
    }

    private static String getHeldItemId(Player player) {
        ItemContainer hotbar = player.getInventory().getHotbar();
        byte active = player.getInventory().getActiveHotbarSlot();

        ItemStack stack = hotbar.getItemStack((short) active);
        if (stack == null) return null;

        return stack.getItemId();
    }
}
