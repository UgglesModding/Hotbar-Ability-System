package com.example.exampleplugin;

import com.example.exampleplugin.AbilityContext;
import com.example.exampleplugin.AbilityDispatch;
import com.example.exampleplugin.PackagedAbilityData;
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

                String Key = (slot == null) ? null : slot.Key;
                if (Key == null || Key.isBlank()) Key = AbilityRegistry.EMPTY_ITEM_ID;

                s.hotbarItemIds[i] = Key;

                // KEEP: RootInteraction stored for the future system
                s.hotbarRootInteractions[i] = (slot == null) ? null : slot.RootInteraction;

                // Plugin system fields
                s.hotbarAbilityIds[i] = (slot == null) ? null : slot.ID;
                s.hotbarPluginFlags[i] = slot != null && slot.Plugin;

                // NEW: store the rest of the JSON-defined fields
                s.hotbarMaxUses[i] = (slot == null) ? 0 : slot.MaxUses;
                s.hotbarPowerMultipliers[i] = (slot == null) ? 1.0f : slot.PowerMultiplier;
                s.hotbarIcons[i] = (slot == null) ? null : slot.Icon;

                // Runtime uses reset when weapon refreshes
                if (s.hotbarMaxUses[i] > 0) {
                    s.hotbarRemainingUses[i] = s.hotbarMaxUses[i];
                } else {
                    // MaxUses <= 0 => treat as unlimited (RemainingUses not used)
                    s.hotbarRemainingUses[i] = 0;
                }

            } else {
                s.hotbarItemIds[i] = AbilityRegistry.EMPTY_ITEM_ID;
                s.hotbarRootInteractions[i] = null;

                s.hotbarAbilityIds[i] = null;
                s.hotbarPluginFlags[i] = false;

                s.hotbarMaxUses[i] = 0;
                s.hotbarPowerMultipliers[i] = 1.0f;
                s.hotbarIcons[i] = null;
                s.hotbarRemainingUses[i] = 0;
            }
        }

        s.selectedAbilitySlot = 1;

        playerRef.sendMessage(Message.raw(
                "[AbilityBar] Loaded held=" + heldItemId +
                        " slot1Key=" + s.hotbarItemIds[0] +
                        " slot1Id=" + s.hotbarAbilityIds[0] +
                        " slot1Plugin=" + s.hotbarPluginFlags[0] +
                        " slot1MaxUses=" + s.hotbarMaxUses[0] +
                        " slot1Power=" + s.hotbarPowerMultipliers[0] +
                        " slot1Root=" + s.hotbarRootInteractions[0]
        ));
    }

    public void useSlot(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref, World world, int slot1to9) {
        var s = state.get(playerRef.getUsername());

        if (slot1to9 < 1 || slot1to9 > 9) return;
        int slot0to8 = slot1to9 - 1;
        s.selectedAbilitySlot = slot1to9;

        // Plugin dispatch info
        boolean Plugin = s.hotbarPluginFlags[slot0to8];
        String ID = s.hotbarAbilityIds[slot0to8];

        // KEEP: RootInteraction info (not used for plugin dispatch)
        String RootInteraction = s.hotbarRootInteractions[slot0to8];

        playerRef.sendMessage(Message.raw(
                "[Ability] slot=" + slot1to9 +
                        " Plugin=" + Plugin +
                        " ID=" + (ID == null ? "null" : ID) +
                        " RootInteraction=" + (RootInteraction == null ? "null" : RootInteraction)
        ));

        // If Plugin=true => do plugin pipeline (RootInteraction ignored here for now)
        if (Plugin) {
            if (ID == null || ID.isBlank()) {
                playerRef.sendMessage(Message.raw("[Ability] Plugin=true but ID missing."));
                return;
            }

            int MaxUses = s.hotbarMaxUses[slot0to8];
            int RemainingUses = s.hotbarRemainingUses[slot0to8];

            // Enforce uses only if MaxUses > 0
            if (MaxUses > 0 && RemainingUses <= 0) {
                playerRef.sendMessage(Message.raw("[Ability] Out of uses for ID=" + ID));
                return;
            }

            String Key = s.hotbarItemIds[slot0to8];
            float PowerMultiplier = s.hotbarPowerMultipliers[slot0to8];
            String Icon = s.hotbarIcons[slot0to8];

            PackagedAbilityData Data = new PackagedAbilityData(
                    slot0to8,
                    Key,
                    ID,
                    MaxUses,
                    PowerMultiplier,
                    RootInteraction,
                    RemainingUses
            );


            world.execute(() -> {
                AbilityContext ctx = AbilityContext.from(playerRef, store, ref, world);

                boolean handled = AbilityDispatch.dispatch(Data, ctx);
                if (!handled) {
                    playerRef.sendMessage(Message.raw("[Ability] No plugin handled ID=" + ID));
                    return;
                }

                // Consume a use ONLY if limited
                if (MaxUses > 0) {
                    int newRemaining = Math.max(0, s.hotbarRemainingUses[slot0to8] - 1);
                    s.hotbarRemainingUses[slot0to8] = newRemaining;

                    playerRef.sendMessage(Message.raw(
                            "[Ability] Used " + ID + " (" + newRemaining + "/" + MaxUses + " left)"
                    ));
                }
            });

            return;
        }

        // Plugin=false: KEEP your existing RootInteraction executor behavior
        if (RootInteraction == null || RootInteraction.isBlank()) return;

        world.execute(() -> {
            boolean ok = interactionExecutor.execute(RootInteraction, playerRef, store, ref, world);
            if (!ok) {
                playerRef.sendMessage(Message.raw("[Ability] No handler registered for RootInteraction: " + RootInteraction));
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
